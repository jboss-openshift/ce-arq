/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.arquillian.ce.runinpod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.api.MountSecret;
import org.jboss.arquillian.ce.api.RunInPod;
import org.jboss.arquillian.ce.api.RunInPodDeployment;
import org.jboss.arquillian.ce.spi.WebSPIConfiguration;
import org.jboss.arquillian.ce.spi.WebSPIContainer;
import org.jboss.arquillian.ce.spi.WildFlySPIConfiguration;
import org.jboss.arquillian.ce.spi.WildFlySPIContainer;
import org.jboss.arquillian.ce.utils.Archives;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.ReflectionUtils;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.deployment.DeploymentDescription;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.test.impl.domain.ProtocolDefinition;
import org.jboss.arquillian.container.test.impl.domain.ProtocolRegistry;
import org.jboss.arquillian.container.test.spi.TestDeployment;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.deployment.ProtocolArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.container.ClassContainer;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class RunInPodUtils {
    private static final Logger log = Logger.getLogger(RunInPodUtils.class.getName());

    private static final String DEFAULT_ENV = Strings.getSystemPropertyOrEnvVar("runinpod.default.env", "eap");
    private static final String DEFAULT_NAME = Strings.getSystemPropertyOrEnvVar("runinpod.default.name", "runinpod.war");

    private final DeployableContainer<?> container;
    private final ServiceLoader serviceLoader;
    private final ProtocolRegistry protocolRegistry;
    private final TestClass testClass;

    public RunInPodUtils(DeployableContainer<?> container, ServiceLoader serviceLoader, ProtocolRegistry protocolRegistry, TestClass testClass) {
        this.container = container;
        this.serviceLoader = serviceLoader;
        this.protocolRegistry = protocolRegistry;
        this.testClass = testClass;
    }

    //---

    public static boolean hasRunInPod(Class<?> clazz) {
        if (clazz == Object.class) {
            return false;
        }

        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (isRunInPod(clazz, m)) {
                return true;
            }
        }

        return hasRunInPod(clazz.getSuperclass());
    }

    public static boolean isRunInPod(Class<?> clazz, Method method) {
        return clazz.isAnnotationPresent(RunInPod.class) || method.isAnnotationPresent(RunInPod.class);
    }

    //---

    public MountSecret readMountSecret() {
        return testClass.getAnnotation(MountSecret.class);
    }

    public RunInPodContainer createContainer(Configuration orignal) {
        Method method = findRunInContainerDeploymentMethod(testClass.getJavaClass());

        String env = DEFAULT_ENV;
        if (method != null) {
            RunInPodDeployment runInPodDeployment = method.getAnnotation(RunInPodDeployment.class);
            env = (runInPodDeployment != null) ? runInPodDeployment.env() : DEFAULT_ENV;
        }

        DeployableContainer<? extends Configuration> container;
        switch (env) {
            case "eap" :
                WildFlySPIContainer wfc = new WildFlySPIContainer();
                WildFlySPIConfiguration wfConfiguration = new WildFlySPIConfiguration();
                merge(wfConfiguration, orignal);
                wfc.setup(wfConfiguration);
                container = wfc;
                break;
            case "jws" :
                WebSPIContainer webc = new WebSPIContainer();
                WebSPIConfiguration webConfiguration = new WebSPIConfiguration();
                merge(webConfiguration, orignal);
                webc.setup(webConfiguration);
                container = webc;
                break;
            default:
                throw new IllegalArgumentException(String.format("No such env '%s' -- cannot create RunInPod container!", env));
        }

        return new NewRunInPodContainer(container, getRunInPodArchive(method));
    }

    public <T extends Configuration> RunInPodContainer createContainer(DeployableContainer<T> container) {
        return new RunningRunInPodContainer(container, getRunInPodArchive(testClass.getJavaClass()));
    }

    private Archive<?> applyProcessors(Archive<?> applicationArchive) {
        List<Archive<?>> auxiliaryArchives = loadAuxiliaryArchives();

        ProtocolDescription protocolDescription = container.getDefaultProtocol();
        ProtocolDefinition protocolDefinition = protocolRegistry.getProtocol(protocolDescription);
        Protocol<?> protocol = protocolDefinition.getProtocol();
        DeploymentPackager packager = protocol.getPackager();

        applyApplicationProcessors(applicationArchive);
        applyAuxiliaryProcessors(auxiliaryArchives);

        try {
            // this should be made more reliable, does not work with e.g. a EnterpriseArchive
            if (ClassContainer.class.isInstance(applicationArchive)) {
                ClassContainer<?> classContainer = ClassContainer.class.cast(applicationArchive);
                classContainer.addClass(testClass.getJavaClass());
            }
        } catch (UnsupportedOperationException e) {
            /*
             * Quick Fix: https://jira.jboss.org/jira/browse/ARQ-118
             * Keep in mind when rewriting for https://jira.jboss.org/jira/browse/ARQ-94
             * that a ShrinkWrap archive might not support a Container if even tho the
             * ContianerBase implements it. Check the Archive Interface..
             */
        }

        DeploymentDescription description = new DeploymentDescription("_DEFAULT_", applicationArchive);
        description.setProtocol(protocolDescription);

        packager.generateDeployment(
            new TestDeployment(description, applicationArchive, auxiliaryArchives),
            serviceLoader.all(ProtocolArchiveProcessor.class)
        );

        return applicationArchive;
    }

    private List<Archive<?>> loadAuxiliaryArchives() {
        List<Archive<?>> archives = new ArrayList<>();

        // load based on the Containers ClassLoader
        Collection<AuxiliaryArchiveAppender> archiveAppenders = serviceLoader.all(AuxiliaryArchiveAppender.class);

        for (AuxiliaryArchiveAppender archiveAppender : archiveAppenders) {
            Archive<?> auxiliaryArchive = archiveAppender.createAuxiliaryArchive();
            if (auxiliaryArchive != null) {
                archives.add(auxiliaryArchive);
            }
        }
        return archives;
    }

    private void applyApplicationProcessors(Archive<?> applicationArchive) {
        Collection<ApplicationArchiveProcessor> processors = serviceLoader.all(ApplicationArchiveProcessor.class);
        for (ApplicationArchiveProcessor processor : processors) {
            processor.process(applicationArchive, testClass);
        }
    }

    private void applyAuxiliaryProcessors(List<Archive<?>> auxiliaryArchives) {
        Collection<AuxiliaryArchiveProcessor> processors = serviceLoader.all(AuxiliaryArchiveProcessor.class);
        for (AuxiliaryArchiveProcessor processor : processors) {
            for (Archive<?> auxiliaryArchive : auxiliaryArchives) {
                processor.process(auxiliaryArchive);
            }
        }
    }

    private static void merge(Configuration configuration, Configuration original) {
        configuration.setNamespace(original.getNamespace());
    }

    private Archive<?> getRunInPodArchive(Class<?> clazz) {
        Method m = findRunInContainerDeploymentMethod(clazz);
        return getRunInPodArchive(m);
    }

    private Archive<?> getRunInPodArchive(Method m) {
        try {
            Archive<?> archive = (m != null) ? ((Archive<?>) m.invoke(null)) : Archives.generateDummyWebArchive();
            applyProcessors(archive);
            return Archives.toProxy(archive, DEFAULT_NAME);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method findRunInContainerDeploymentMethod(Class<?> clazz) {
        Method m = ReflectionUtils.findAnnotatedMethod(clazz, RunInPodDeployment.class);
        if (m != null) {
            return m;
        }

        m = ReflectionUtils.findDeploymentMethod(clazz);
        if (m != null) {
            return m;
        }

        log.info("No RunInPod deployment method found: " + clazz.getName());
        return null;
    }
}
