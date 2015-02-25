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

package org.jboss.arquillian.ce.wildfly;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerManifest;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodState;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.Port;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerState;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.jboss.arquillian.ce.utils.K8sClient;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.protocol.servlet.ServletMethodExecutor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.application5.ApplicationDescriptor;
import org.jboss.shrinkwrap.descriptor.api.application5.ModuleType;
import org.jboss.shrinkwrap.descriptor.api.application5.WebType;
import org.kohsuke.MetaInfServices;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@MetaInfServices
public class WildFlyCEContainer implements DeployableContainer<WildFlyCEConfiguration> {
    private WildFlyCEConfiguration configuration;
    private K8sClient client;

    public Class<WildFlyCEConfiguration> getConfigurationClass() {
        return WildFlyCEConfiguration.class;
    }

    public void setup(WildFlyCEConfiguration configuration) {
        this.configuration = configuration;
        this.client = new K8sClient(configuration);
    }

    public void start() throws LifecycleException {
    }

    public void stop() throws LifecycleException {
    }

    public ProtocolDescription getDefaultProtocol() {
        return ProtocolDescription.DEFAULT;
    }

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        try {
            Map<Object, Object> properties = new HashMap<>();
            properties.put("from.name", System.getProperty("from.name", "docker-registry.usersys.redhat.com/cloud_enablement/openshift-jboss-eap:6.4"));
            properties.put("deployment.dir", System.getProperty("deployment.dir", "/opt/eap/standalone/deployments/"));
            properties.putAll(configuration.getProperties());

            InputStream dockerfileTemplate = WildFlyCEConfiguration.class.getClassLoader().getResourceAsStream("Dockerfile_template");
            String imageName = client.pushImage(dockerfileTemplate, archive, properties);

            final String apiVersion = "v1beta1";

            client.deployService("http-service", apiVersion, 80, 8080, Collections.singletonMap("name", "eapPod"));
            client.deployService("https-service", apiVersion, 443, 8443, Collections.singletonMap("name", "eapPod"));

            List<Port> ports = new ArrayList<>();
            Port http = new Port();
            http.setHostPort(9080);
            http.setContainerPort(8080);
            ports.add(http);
            Port https = new Port();
            https.setHostPort(9443);
            https.setContainerPort(8443);
            ports.add(https);

            List<EnvVar> envVars = Collections.emptyList();
            Container container = client.createContainer(imageName, "eap-container", envVars, ports, Collections.<VolumeMount>emptyList());

            List<Container> containers = Collections.singletonList(container);
            ContainerManifest cm = client.createContainerManifest("eapPod", apiVersion, containers);

            PodState podState = client.createPodState(cm);
            Map<String, String> podLabels = Collections.singletonMap("name", "eapPod");

            PodTemplate podTemplate = client.createPodTemplate(podLabels, podState);

            int replicas = 1;
            Map<String, String> selector = Collections.singletonMap("name", "eapPod");
            ReplicationControllerState desiredState = client.createReplicationControllerState(replicas, selector, podTemplate);

            Map<String, String> labels = Collections.singletonMap("name", "eapController");
            ReplicationController rc = client.createReplicationController("", apiVersion, labels, desiredState);

            client.deployReplicationController(rc);

            String host = client.getService("http-service").getPortalIP();

            HTTPContext context = new HTTPContext(host, 80);
            addServlets(context, archive);

            ProtocolMetaData pmd = new ProtocolMetaData();
            pmd.addContext(context);
            return pmd;
        } catch (Exception e) {
            throw new DeploymentException("Cannot deploy in CE env.", e);
        }
    }

    private void addServlets(HTTPContext context, Archive<?> archive) throws Exception {
        if (archive instanceof WebArchive) {
            handleWebArchive(context, WebArchive.class.cast(archive));
        } else if (archive instanceof EnterpriseArchive) {
            handleEAR(context, EnterpriseArchive.class.cast(archive));
        }
    }

    private void handleWebArchive(HTTPContext context, WebArchive war) {
        String name = war.getName();
        int p = name.lastIndexOf("."); // drop .war
        handleWebArchive(context, war, name.substring(0, p));
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
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
    }

}
