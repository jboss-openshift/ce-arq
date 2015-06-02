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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Service;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WildFlyCEContainer extends AbstractCEContainer<WildFlyCEConfiguration> {
    public Class<WildFlyCEConfiguration> getConfigurationClass() {
        return WildFlyCEConfiguration.class;
    }

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        try {
            String imageName = buildImage(archive, "docker-registry.usersys.redhat.com/cloud_enablement/openshift-jboss-eap:6.4", "/opt/eap/standalone/deployments/");

            final Service.ApiVersion apiVersion = Service.ApiVersion.fromValue(configuration.getApiVersion());

            // clean old k8s stuff
            cleanup();

            // add new k8s config

            client.deployService("http-service", apiVersion, "http", 80, 8080, Collections.singletonMap("name", "eapPod"));
            client.deployService("https-service", apiVersion, "https", 443, 8443, Collections.singletonMap("name", "eapPod"));
            client.deployService("mgmt-service", apiVersion, "mgmt", 9990, configuration.getMgmtPort(), Collections.singletonMap("name", "eapPod"));

            List<ContainerPort> ports = new ArrayList<>();
            // http
            ContainerPort http = new ContainerPort();
            http.setName("http");
            http.setContainerPort(8080);
            ports.add(http);
            // https / ssl
            ContainerPort https = new ContainerPort();
            https.setName("https");
            https.setContainerPort(8443);
            ports.add(https);
            // DMR / management
            ContainerPort mgmt = new ContainerPort();
            mgmt.setName("mgmt");
            mgmt.setContainerPort(configuration.getMgmtPort());
            ports.add(mgmt);

            deployPod(imageName, ports, "eap", 1, configuration.getPreStopHookType(), configuration.getPreStopPath());

            return getProtocolMetaData(archive);
        } catch (Exception e) {
            throw new DeploymentException("Cannot deploy in CE env.", e);
        }
    }

    protected void cleanup() throws Exception {
        client.cleanServices("http-service", "https-service", "mgmt-service");
        client.cleanReplicationControllers("eaprc");
        client.cleanPods("eaprc");
    }

}
