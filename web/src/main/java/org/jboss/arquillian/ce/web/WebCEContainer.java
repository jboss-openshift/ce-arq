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

package org.jboss.arquillian.ce.web;

import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.ContainerPort;
import org.jboss.arquillian.ce.protocol.CEServletProtocol;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WebCEContainer extends AbstractCEContainer<WebCEConfiguration> {
    public Class<WebCEConfiguration> getConfigurationClass() {
        return WebCEConfiguration.class;
    }

    public void apply(OutputStream outputStream) {
        // nothing atm
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(CEServletProtocol.PROTOCOL_NAME);
    }

    public ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException {
        try {
            String imageName = buildImage(archive, "registry.access.redhat.com/jboss-webserver-3/tomcat8-openshift:3.0", "/opt/webserver/webapps/");

            // clean old k8s stuff
            cleanup();

            // add new k8s config

            // http
            ContainerPort http = new ContainerPort();
            http.setName("http");
            http.setContainerPort(8080);
            List<ContainerPort> ports = Collections.singletonList(http);

            String rc = deployReplicationController(imageName, ports, "jws", 1, null, null, true);
            log.info("Deployed replication controller: " + rc);

            return getProtocolMetaData(archive, 1);
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy in CE env.", t);
        }
    }

    protected void cleanup() throws Exception {
        client.cleanReplicationControllers("jwsrc");
        client.cleanPods("jwsrc");
    }

}
