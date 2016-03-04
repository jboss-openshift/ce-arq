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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.adapter.DockerAdapter;
import org.jboss.arquillian.ce.adapter.DockerAdapterContext;
import org.jboss.arquillian.ce.adapter.DockerAdapterImpl;
import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.adapter.OpenShiftAdapterFactory;
import org.jboss.arquillian.ce.api.ConfigurationHandle;
import org.jboss.arquillian.ce.api.MountSecret;
import org.jboss.arquillian.ce.api.Replicas;
import org.jboss.arquillian.ce.proxy.Proxy;
import org.jboss.arquillian.ce.resources.OpenShiftResourceFactory;
import org.jboss.arquillian.ce.runinpod.RunInPodContainer;
import org.jboss.arquillian.ce.runinpod.RunInPodUtils;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.container.test.impl.domain.ProtocolRegistry;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.protocol.servlet.ServletMethodExecutor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filter;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
    private InstanceProducer<ConfigurationHandle> configurationInstanceProducer;

    @Inject
    protected Instance<TestClass> tc;

    @Inject
    private Instance<ServiceLoader> serviceLoader;

    @Inject
    private Instance<ProtocolRegistry> protocolRegistry;

    protected T configuration;
    protected OpenShiftAdapter client;
    protected DockerAdapter dockerAdapter;
    protected Proxy proxy;
    protected boolean shouldRemoveProject;

    protected RunInPodUtils runInPodUtils;
    protected RunInPodContainer runInPodContainer;
    protected final ParallelHandler parallelHandler;

    public AbstractCEContainer() {
        this(new ParallelHandler());
    }

    protected AbstractCEContainer(ParallelHandler parallelHandler) {
        this.parallelHandler = parallelHandler;
    }

    protected String getName(String prefix, Archive<?> archive) {
        String name = archive.getName();
        int p = name.lastIndexOf(".");
        String mid = name.substring(0, p) + name.substring(p + 1);
        return toK8sName(prefix, mid);
    }

    /**
     * Return k8s compatible name.
     */
    protected static String toK8sName(String prefix, String name) {
        name = name.replace("_", "-");
        name = (prefix + name);
        // limit to 50, as full is 63
        int min = Math.min(name.length(), 50);
        return name.substring(0, min).toLowerCase();
    }

    protected abstract String getPrefix();

    public void setup(T configuration) {
        this.configuration = getConfigurationClass().cast(configuration);
        // provide configuration
        if (this.configurationInstanceProducer != null) {
            // prevent setters access
            this.configurationInstanceProducer.set(BytecodeUtils.narrow(ConfigurationHandle.class, configuration));
        }
    }

    public void start() throws LifecycleException {
        this.client = OpenShiftAdapterFactory.getOpenShiftAdapter(configuration);
        this.proxy = client.createProxy();

        RegistryLookup lookup;
        if ("static".equalsIgnoreCase(configuration.getRegistryType())) {
            lookup = new StaticRegistryLookup(configuration);
        } else {
            lookup = client;
        }
        this.dockerAdapter = new DockerAdapterImpl(configuration, lookup);

        String namespace = configuration.getNamespace();
        log.info("Using Kubernetes namespace / project: " + namespace);

        shouldRemoveProject = client.checkProject(); // create project, if it doesn't exist yet
    }

    public void stop() throws LifecycleException {
        try {
            if (runInPodContainer != null) {
                runInPodContainer.stop();
            }
        } finally {
            try {
                if (shouldRemoveProject && configuration.performCleanup()) {
                    client.deleteProject();
                }
            } finally {
                try {
                    if (dockerAdapter != null) {
                        try {
                            dockerAdapter.close();
                        } catch (IOException ignore) {
                        }
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
        }
    }

    protected abstract ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException;

    protected RunInPodContainer create() {
        return runInPodUtils.createContainer(this);
    }

    /**
     * Are we the container for @RunInPod handling?
     */
    protected boolean isSPI() {
        // not injected by ARQ, or we're in progress in RunInPod
        return (tc == null) || (runInPodContainer != null && runInPodContainer.inProgress());
    }

    /**
     * Only handle this now, as tc finally has TestClass injected.
     */
    private void handleRunInPod() throws DeploymentException {
        if (!isSPI() && RunInPodUtils.hasRunInPod(tc.get().getJavaClass())) {
            log.info("Found @RunInPod, setting up utils, container ...");
            runInPodUtils = new RunInPodUtils(this, serviceLoader.get(), protocolRegistry.get(), tc.get());
            runInPodContainer = create();
            try {
                runInPodContainer.start();
            } catch (LifecycleException e) {
                throw new DeploymentException("Cannot start RunInPodContainer!", e);
            }
        }
    }

    private void handleResources(Archive<?> archive) {
        if (!isSPI()) {
            OpenShiftResourceFactory.createResources(archive.getName(), client, archive, tc.get().getJavaClass(), configuration.getProperties());
        }
    }

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        try {
            return deployInternal(archive);
        } finally {
            // reset parallel handler
            if (isSPI()) {
                parallelHandler.clearSPI();
            } else if (runInPodContainer != null) {
                parallelHandler.clearMain();
            }
        }
    }

    private ProtocolMetaData deployInternal(Archive<?> archive) throws DeploymentException {
        dockerAdapter.prepare(archive);

        handleResources(archive);

        handleRunInPod();
        if (runInPodContainer != null && !isSPI()) {
            parallelHandler.initMain();
            runInPodUtils.parallelize(runInPodContainer, parallelHandler);
        }

        final ProtocolMetaData protocolMetaData;
        try {
            protocolMetaData = doDeploy(archive);
        } catch (DeploymentException e) {
            if (!isSPI()) {
                parallelHandler.errorInMain(e);
            }
            throw e;
        }

        if (isSPI()) {
            // resume SPI since it is ready -- Main can move on
            parallelHandler.resumeOnSPI();
        }

        if (runInPodContainer != null && !isSPI()) {
            // reset
            parallelHandler.initSPI();
            // wait for runinpod to finish
            parallelHandler.waitOnSPI();

            // check if we got some error in SPI / parallel handling
            Throwable error = parallelHandler.getErrorFromSPI();
            if (error != null) {
                if (error instanceof DeploymentException) {
                    throw DeploymentException.class.cast(error);
                } else {
                    throw new DeploymentException("Error in SPI deployment.", error);
                }
            }
        }

        return protocolMetaData;
    }

    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(Constants.PROTOCOL_NAME);
    }

    protected int readReplicas() {
        if (isSPI()) {
            return 1; // @RunInPod
        }

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

    protected MountSecret readMountSecret() {
        if (isSPI()) {
            if (runInPodUtils == null) {
                return null;
            } else {
                return runInPodUtils.readMountSecret();
            }
        } else {
            TestClass testClass = tc.get();
            return (testClass != null) ? testClass.getAnnotation(MountSecret.class) : null;
        }
    }

    protected InputStream getDockerTemplate() {
        return getClass().getClassLoader().getResourceAsStream(configuration.getTemplateName());
    }

    protected String buildImage(Archive<?> archive, String parent, String dir) throws IOException {
        Properties properties = configuration.getProperties();

        String from = Strings.toValue(configuration.getFromParent(), parent);
        properties.put("from.name", from);
        String deployment = Strings.toValue(configuration.getDeploymentDir(), dir);
        properties.put("deployment.dir", deployment);

        log.info(String.format("FROM %s [%s]", from, deployment));

        InputStream dockerfileTemplate = getDockerTemplate();

        DockerAdapterContext context = new DockerAdapterContext(this, dockerfileTemplate, archive, properties, isSPI() ? "spi-" : "");
        return dockerAdapter.buildAndPushImage(context);
    }

    protected String deployResourceContext(RCContext context) throws Exception {
        // wait for Main to finish, if we're @RunInPod container
        if (isSPI()) {
            parallelHandler.waitOnMain();

            Throwable error = parallelHandler.getErrorFromMain();
            if (error != null) {
                if (error instanceof Exception) {
                    throw Exception.class.cast(error);
                } else {
                    throw new Exception(error);
                }
            }
        }

        String name = getName(getPrefix(), context.getArchive());
        int replicas = context.getReplicas();
        if (replicas <= 0) {
            throw new IllegalArgumentException(String.format("Invalid # of replicas: %s", replicas));
        } else if (replicas == 1) {
            return client.deployPod(name, getPrefix(), context);
        } else {
            return client.deployReplicationController(name, getPrefix(), context);
        }
    }

    protected ProtocolMetaData getProtocolMetaData(Archive<?> archive, final Map<String, String> labels, final int replicas) throws Exception {
        log.info("Creating ProtocolMetaData ...");

        if (!isSPI()) {
            // resume after Main already pushed k8s/ose config
            parallelHandler.resumeOnMain();
        }

        delay(labels, replicas);

        return getProtocolMetaData(archive, labels);
    }

    protected ProtocolMetaData getProtocolMetaData(Archive<?> archive, final Map<String, String> labels) throws Exception {
        HTTPContext context = new HTTPContext("<DUMMY>", 80); // we don't use the host, as we use proxy
        addServlets(context, archive);

        ProtocolMetaData pmd = new ProtocolMetaData();
        // we need original configuration instance; due to generated values
        pmd.addContext(new DeploymentContext(archive, labels, proxy));
        pmd.addContext(context);
        pmd.addContext(proxy.createManagementHandle(labels));
        return pmd;
    }

    protected void delay(final Map<String, String> labels, final int replicas) throws Exception {
        Containers.delay(configuration.getStartupTimeout(), 4000L, new Checker() {
            public boolean check() {
                Set<String> pods = proxy.getReadyPods(labels);
                boolean result = (pods.size() >= replicas);
                if (result) {
                    log.info(String.format("Pods are ready: %s", pods));
                }
                return result;
            }

            @Override
            public String toString() {
                return String.format("(Required pods: %s)", replicas);
            }
        });
    }

    protected void addServlets(HTTPContext context, Archive<?> archive) throws Exception {
        if (archive instanceof WebArchive) {
            handleWebArchive(context, WebArchive.class.cast(archive), false);
        } else if (archive instanceof EnterpriseArchive) {
            handleEAR(context, EnterpriseArchive.class.cast(archive));
        }
    }

    protected String toContextRoot(WebArchive war) {
        String name = war.getName();
        String contextRoot = "";
        // ROOT --> "/"
        if ("ROOT.war".equals(name) == false) {
            int p = name.lastIndexOf("."); // drop .war
            contextRoot = name.substring(0, p);
        }
        return contextRoot;
    }

    private void handleWebArchive(HTTPContext context, WebArchive war, boolean check) {
        handleWebArchive(context, war, toContextRoot(war), check);
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
                        handleWebArchive(context, war, web.getContextRoot(), true);
                    }
                }
            }
        } else {
            Collection<WebArchive> wars = ear.getAsType(WebArchive.class, new Filter<ArchivePath>() {
                @Override
                public boolean include(ArchivePath path) {
                    return path.get().endsWith(".war");
                }
            });
            for (WebArchive war : wars) {
                handleWebArchive(context, war, true);
            }
        }
    }

    @SuppressWarnings("UnusedParameters")
    private void handleWebArchive(HTTPContext context, WebArchive war, String contextRoot, boolean check) {
        String info = "Adding Arquillian servlet for .war: " + war.getName();
        boolean add = true;
        if (check && tc != null && tc.get() != null) {
            String classPath = tc.get().getName().replace(".", "/") + ".class";
            add = findTestClass(war, classPath);
            info = String.format("%s [%s]", info, classPath);
        }
        if (add) {
            log.info(info);
            Servlet arqServlet = new Servlet(ServletMethodExecutor.ARQUILLIAN_SERVLET_NAME, contextRoot);
            context.add(arqServlet);
        }
    }

    protected boolean findTestClass(WebArchive war, String classPath) {
        Node inClasses = war.get("WEB-INF/classes/" + classPath);
        if (inClasses != null) {
            return true;
        }
        Node lib = war.get("WEB-INF/lib");
        if (lib != null) {
            for (Node child : lib.getChildren()) {
                ArchivePath path = child.getPath();
                if (path.get().endsWith(".jar")) {
                    JavaArchive jar = war.getAsType(JavaArchive.class, path);
                    Node inJar = jar.get(classPath);
                    if (inJar != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected void cleanup(Archive<?> archive) throws Exception {
        String name = getName(getPrefix(), archive) + "rc";
        client.cleanReplicationControllers(name);
        client.cleanPods(DeploymentContext.getDeploymentLabels(archive));
    }

    protected void cleanupResources(Archive<?> archive) {
        if (!isSPI()) {
            client.deleteResources(archive.getName());
        }
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
        try {
            if (runInPodContainer != null) {
                runInPodContainer.undeploy();
            }
        } finally {
            // do we keep test config around for some more?
            if (configuration.performCleanup()) {
                try {
                    cleanupResources(archive);
                } finally {
                    try {
                        cleanup(archive);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                log.info("Ignore Kubernetes cleanup -- test config is still available.");
            }
            dockerAdapter.reset(archive);
        }
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
    }

}
