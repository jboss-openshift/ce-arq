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

package org.jboss.arquillian.ce.spi;

import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.ce.utils.DeploymentContext;
import org.jboss.arquillian.ce.utils.Port;
import org.jboss.arquillian.ce.utils.RCContext;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;

/**
 * We have this SPI containers so we can delegate @RunInPod deployment ot them.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WebSPIContainer extends AbstractCEContainer<WebSPIConfiguration> {
    public Class<WebSPIConfiguration> getConfigurationClass() {
        return WebSPIConfiguration.class;
    }

    public void apply(OutputStream outputStream) {
        // nothing atm
    }

    public ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException {
        try {
            String imageName = buildImage(archive, "ce-registry.usersys.redhat.com/jboss-webserver-3/webserver30-tomcat8-openshift:1.2", "/opt/webserver/webapps/");

            // clean old k8s stuff
            cleanup(archive);

            // add new k8s config

            // http
            Port http = new Port();
            http.setName("http");
            http.setContainerPort(8080);
            List<Port> ports = Collections.singletonList(http);

            Map<String, String> labels = DeploymentContext.getDeploymentLabels(archive);

            RCContext context = new RCContext(archive, imageName, ports, labels, 1);

            context.setProbeHook(configuration.getProbeHookType());
            context.setProbeCommands(configuration.getProbeCommands());

            String rc = deployResourceContext(context);
            log.info("Deployed k8s resource: " + rc);

            return getProtocolMetaData(archive, labels, 1);
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy in CE env.", t);
        }
    }

    protected String getPrefix() {
        return "jws";
    }

}
