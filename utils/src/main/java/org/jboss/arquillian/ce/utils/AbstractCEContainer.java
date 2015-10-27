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

package org.jboss.arquillian.ce.utils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.api.Replicas;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.protocol.servlet.ServletMethodExecutor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.application7.ApplicationDescriptor;
import org.jboss.shrinkwrap.descriptor.api.application7.ModuleType;
import org.jboss.shrinkwrap.descriptor.api.application7.WebType;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractCEContainer<T extends Configuration> implements DeployableContainer<T>, DockerFileTemplateHandler {
    protected final Logger log = Logger.getLogger(getClass().getName());

    @Inject
    @ApplicationScoped
    private InstanceProducer<Configuration> configurationInstanceProducer;

    @Inject
    protected Instance<TestClass> tc;

    protected T configuration;
    protected OpenShiftAdapter client;
    protected Proxy proxy;

    protected String getName(String prefix, Archive<?> archive) {
        String name = archive.getName();
        int p = name.lastIndexOf(".");
        return (prefix + name.substring(0, p) + name.substring(p + 1)).toLowerCase();
    }

    protected abstract String getPrefix();

    protected void cleanup(Archive<?> archive) throws Exception {
        String name = getName(getPrefix(), archive) + "rc";
        client.cleanReplicationControllers(name);
        client.cleanPods(AbstractOpenShiftAdapter.getDeploymentLabels(archive));
    }

    public void setup(T configuration) {
        this.configuration = getConfigurationClass().cast(configuration);
        this.configurationInstanceProducer.set(configuration);
    }

    public void start() throws LifecycleException {
        this.client = OpenShiftAdapterFactory.getOpenShiftAdapter(configuration);
        this.proxy = client.createProxy();

        String namespace = configuration.getNamespace();
        log.info("Using Kubernetes namespace / project: " + namespace);

        if (configuration.isGeneratedNS()) {
            client.createProject(namespace);
        }
    }

    public void stop() throws LifecycleException {
        try {
            if (configuration.isGeneratedNS()) {
                client.deleteProject(configuration.getNamespace());
            }
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                //noinspection ThrowFromFinallyBlock
                throw new LifecycleException("Error closing Kubernetes client.", e);
            }
        }
    }

    protected abstract ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException;

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        client.prepare(archive);
        return doDeploy(archive);
    }

    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("Servlet 3.0");
    }

    protected int readReplicas() {
        TestClass testClass = tc.get();
        Replicas replicas = testClass.getAnnotation(Replicas.class);
        int r = -1;
        if (replicas != null) {
            if (replicas.value() <= 0) {
                throw new IllegalArgumentException("Non-positive replicas size: " + replicas.value());
            }
            r = replicas.value();
        }
        int max = 0;
        for (Method c : testClass.getMethods(TargetsContainer.class)) {
            int index = Strings.parseNumber(c.getAnnotation(TargetsContainer.class).value());
            if (r > 0 && index >= r) {
                throw new IllegalArgumentException(String.format("Node / pod index bigger then replicas; %s >= %s ! (%s)", index, r, c));
            }
            max = Math.max(max, index);
        }
        if (r < 0) {
            return max + 1;
        } else {
            return r;
        }
    }

    protected String buildImage(Archive<?> archive, String parent, String dir) throws IOException {
        Properties properties = new Properties();

        String from = Strings.toValue(configuration.getFromParent(), parent);
        properties.put("from.name", from);
        String deployment = Strings.toValue(configuration.getDeploymentDir(), dir);
        properties.put("deployment.dir", deployment);
        configuration.apply(properties);

        log.info(String.format("FROM %s [%s]", from, deployment));

        InputStream dockerfileTemplate = getClass().getClassLoader().getResourceAsStream("Dockerfile_template");
        return client.buildAndPushImage(this, dockerfileTemplate, archive, properties);
    }

    protected String deployReplicationController(RCContext context) throws Exception {
        String name = getName(getPrefix(), context.getArchive());
        return client.deployReplicationController(name, AbstractOpenShiftAdapter.getDeploymentLabels(context.getArchive()), getPrefix(), context);
    }

    protected ProtocolMetaData getProtocolMetaData(Archive<?> archive, final int replicas) throws Exception {
        log.info("Creating ProtocolMetaData ...");

        final Map<String, String> labels = AbstractOpenShiftAdapter.getDeploymentLabels(archive);

        Containers.delay(configuration.getStartupTimeout(), 4000L, new Checker() {
            public boolean check() {
                return (proxy.getReadyPodsSize(labels, configuration.getNamespace()) >= replicas);
            }

            @Override
            public String toString() {
                return String.format("(Required pods: %s)", replicas);
            }
        });

        HTTPContext context = new HTTPContext("<DUMMY>", 80); // we don't use the host, as we use proxy
        addServlets(context, archive);

        ProtocolMetaData pmd = new ProtocolMetaData();
        pmd.addContext(configuration); // we need original instance; due to generated values
        pmd.addContext(archive);
        pmd.addContext(context);
        return pmd;
    }

    protected void addServlets(HTTPContext context, Archive<?> archive) throws Exception {
        if (archive instanceof WebArchive) {
            handleWebArchive(context, WebArchive.class.cast(archive));
        } else if (archive instanceof EnterpriseArchive) {
            handleEAR(context, EnterpriseArchive.class.cast(archive));
        }
    }

    private void handleWebArchive(HTTPContext context, WebArchive war) {
        String name = war.getName();
        String contextRoot = "";
        // ROOT --> "/"
        if ("ROOT.war".equals(name) == false) {
            int p = name.lastIndexOf("."); // drop .war
            contextRoot = name.substring(0, p);
        }
        handleWebArchive(context, war, contextRoot);
    }

    private void handleEAR(HTTPContext context, EnterpriseArchive ear) throws IOException {
        final Node appXml = ear.get("META-INF/application.xml");
        if (appXml != null) {
            try (InputStream stream = appXml.getAsset().openStream()) {
                ApplicationDescriptor ad = Descriptors.importAs(ApplicationDescriptor.class).fromStream(stream);
                List<ModuleType<ApplicationDescriptor>> allModules = ad.getAllModule();
                for (ModuleType<ApplicationDescriptor> mt : allModules) {
                    WebType<ModuleType<ApplicationDescriptor>> web = mt.getOrCreateWeb();
                    String uri = web.getWebUri();
                    if (uri != null) {
                        WebArchive war = ear.getAsType(WebArchive.class, uri);
                        handleWebArchive(context, war, web.getContextRoot());
                    }
                }
            }
        }
    }

    @SuppressWarnings("UnusedParameters")
    private void handleWebArchive(HTTPContext context, WebArchive war, String contextRoot) {
        Servlet arqServlet = new Servlet(ServletMethodExecutor.ARQUILLIAN_SERVLET_NAME, contextRoot);
        context.add(arqServlet);
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
        // do we keep test config around for some more?
        if (configuration.isIgnoreCleanup() == false) {
            try {
                cleanup(archive);
            } catch (Exception ignored) {
            }
        } else {
            log.info("Ignore Kubernetes cleanup -- test config is still available.");
        }

        client.reset(archive);
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
    }

}
