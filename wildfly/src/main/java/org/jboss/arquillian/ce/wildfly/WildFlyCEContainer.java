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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.arquillian.ce.protocol.CEServletProtocol;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.ce.utils.Port;
import org.jboss.arquillian.ce.utils.RCContext;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WildFlyCEContainer extends AbstractCEContainer<WildFlyCEConfiguration> {
    public Class<WildFlyCEConfiguration> getConfigurationClass() {
        return WildFlyCEConfiguration.class;
    }

    public void apply(OutputStream outputStream) throws IOException {
        String hqEnv = String.format("ENV HORNETQ_CLUSTER_PASSWORD %s", configuration.getHornetQClusterPassword());
        outputStream.write(("\n" + hqEnv + "\n").getBytes());
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(CEServletProtocol.PROTOCOL_NAME);
    }

    public ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException {
        try {
            String imageName = buildImage(archive, "ce-registry.usersys.redhat.com/jboss-eap-6/eap64-openshift:1.2", "/opt/eap/standalone/deployments/");

            // clean old k8s stuff
            cleanup(archive);

            // add new k8s config

            List<Port> ports = new ArrayList<>();
            // http
            Port http = new Port();
            http.setName("http");
            http.setContainerPort(8080);
            ports.add(http);
            // https / ssl
            Port https = new Port();
            https.setName("https");
            https.setContainerPort(8443);
            ports.add(https);
            // DMR / management
            Port mgmt = new Port();
            mgmt.setName("mgmt");
            mgmt.setContainerPort(configuration.getMgmtPort());
            ports.add(mgmt);
            // jgroups / ping
            Port ping = new Port();
            ping.setName("ping");
            ping.setContainerPort(8888);
            ports.add(ping);

            int replicas = readReplicas();

            RCContext context = new RCContext(archive, imageName, ports, replicas);

            context.setLifecycleHook(configuration.getPreStopHookType());
            context.setPreStopPath(configuration.getPreStopPath());
            context.setIgnorePreStop(configuration.isIgnorePreStop());

            context.setProbeHook(configuration.getProbeHookType());
            List<String> probeCommands = configuration.getProbeCommands();
            if (probeCommands == null) {
                probeCommands = Arrays.asList("/bin/bash", "-c", "/opt/eap/bin/readinessProbe.sh");
            }
            context.setProbeCommands(probeCommands);

            String rc = deployReplicationController(context);
            log.info(String.format("Deployed replication controller [%s]: %s", replicas, rc));

            return getProtocolMetaData(archive, replicas);
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy in CE env.", t);
        }
    }

    protected String getPrefix() {
        return "eap";
    }
}
