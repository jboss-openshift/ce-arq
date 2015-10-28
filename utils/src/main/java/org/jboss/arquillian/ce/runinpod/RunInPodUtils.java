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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jboss.arquillian.ce.api.RunInPod;
import org.jboss.arquillian.ce.api.RunInPodDeployment;
import org.jboss.arquillian.ce.spi.WebSPIConfiguration;
import org.jboss.arquillian.ce.spi.WebSPIContainer;
import org.jboss.arquillian.ce.spi.WildFlySPIConfiguration;
import org.jboss.arquillian.ce.spi.WildFlySPIContainer;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class RunInPodUtils {
    private static final String DEFAULT_ENV = Strings.getSystemPropertyOrEnvVar("runinpod.default.env", "eap");

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
        if (clazz.isAnnotationPresent(RunInPod.class)) {
            return true;
        }
        if (method.isAnnotationPresent(RunInPod.class)) {
            return true;
        }
        return false;
    }

    public static <T extends Configuration> RunInPodContainer createContainer(Class<?> clazz, Configuration orignal) {
        Method method = findRunInContainerDeploymentMethod(clazz);
        RunInPodDeployment runInPodDeployment = method.getAnnotation(RunInPodDeployment.class);
        String env = (runInPodDeployment != null) ? runInPodDeployment.env() : DEFAULT_ENV;

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

        return new RunInPodContainer(container, getRunInPodArchive(method));
    }

    public static <T extends Configuration> RunInPodContainer createContainer(DeployableContainer<T> container, Class<?> clazz) {
        return new RunInPodContainer(container, getRunInPodArchive(clazz));
    }

    private static void merge(Configuration configuration, Configuration original) {
        configuration.setNamespace(original.getNamespace());
    }

    private static Archive<?> getRunInPodArchive(Class<?> clazz) {
        Method m = findRunInContainerDeploymentMethod(clazz);
        return getRunInPodArchive(m);
    }

    private static Archive<?> getRunInPodArchive(Method m) {
        try {
            return (Archive<?>) m.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method findRunInContainerDeploymentMethod(Class<?> clazz) {
        Method m = findDeploymentMethodInternal(clazz, RunInPodDeployment.class);
        if (m != null) {
            return m;
        }

        m = findDeploymentMethodInternal(clazz, Deployment.class);
        if (m != null) {
            return m;
        }

        throw new IllegalStateException("No RunInPod deployment method found: " + clazz.getName());
    }

    private static Method findDeploymentMethodInternal(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        if (clazz == Object.class) {
            return null;
        }

        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (Modifier.isStatic(m.getModifiers()) && m.isAnnotationPresent(annotationClass)) {
                return m;
            }
        }

        return findDeploymentMethodInternal(clazz.getSuperclass(), annotationClass);
    }
}
