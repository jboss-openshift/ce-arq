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

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.arquillian.ce.utils.K8sClient;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
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

            client.deployService("http-service", "v1beta1", 80, 8080, Collections.singletonMap("name", "eapPod"));
            client.deployService("https-service", "v1beta1", 443, 8443, Collections.singletonMap("name", "eapPod"));

            // TODO
            return null;
        } catch (Exception e) {
            throw new DeploymentException("Cannot deploy in CE env.", e);
        }
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
    }

}
