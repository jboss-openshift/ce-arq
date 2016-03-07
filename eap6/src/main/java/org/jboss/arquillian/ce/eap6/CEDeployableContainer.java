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

package org.jboss.arquillian.ce.eap6;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Map;

import org.jboss.arquillian.ce.spi.WildFlySPIContainer;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.ce.utils.Archives;
import org.jboss.arquillian.ce.utils.Operator;
import org.jboss.arquillian.ce.utils.RCContext;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.ContainerScoped;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.as.arquillian.container.ArchiveDeployer;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.kohsuke.MetaInfServices;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@MetaInfServices(DeployableContainer.class)
public class CEDeployableContainer extends AbstractCEContainer<CEConfiguration> {
    @Inject
    @ContainerScoped
    private InstanceProducer<ManagementClient> managementClient;

    @Inject
    @ContainerScoped
    private InstanceProducer<ArchiveDeployer> archiveDeployer;

    private Map<String, String> labels;
//    private PortForward.Handle portFwd;

    public Class<CEConfiguration> getConfigurationClass() {
        return CEConfiguration.class;
    }

    public void apply(OutputStream outputStream) throws IOException {
    }

    protected String getPrefix() {
        return "eap6";
    }

    protected ManagementClient getManagementClient() {
        return managementClient.get();
    }

    protected ArchiveDeployer getArchiveDeployer() {
        return archiveDeployer.get();
    }

    @Override
    public void start() throws LifecycleException {
        super.start();

        try {
            int replicas = 1; // single pod
            labels = deployEapPods(replicas);
            client.delay(labels, replicas, Operator.GREATER_THAN_OR_EQUAL);

/*
            PortForwardContext context = client.createPortForwardContext(labels, configuration.getMgmtPort());
            PortForward pf = proxy.createPortForward();
            portFwd = pf.run(context);

            String address = portFwd.getInetAddress().getHostAddress(); // we abuse k8s port forwarding
*/
            String address = InetAddress.getLocalHost().getHostAddress();
            int port = configuration.getMgmtPort();

            ModelControllerClient modelControllerClient = ModelControllerClient.Factory.create(address, port);

            ManagementClient mgmtClient = new ManagementClient(modelControllerClient, address, port);
            managementClient.set(mgmtClient);

            checkEnv(mgmtClient);

            ArchiveDeployer deployer = new ArchiveDeployer(modelControllerClient);
            archiveDeployer.set(deployer);
        } catch (Exception e) {
            throw new LifecycleException("Error setting up EAP6 pod.", e);
        }
    }

    protected void checkEnv(ManagementClient mgmtClient) throws IOException {
        ModelNode op = Util.createOperation("read-resource", PathAddress.pathAddress("core-service", "server-environment"));
        op.get("include-runtime").set(true);
        ModelNode result = mgmtClient.getControllerClient().execute(op);
        log.info(String.format("Env info: %s", result));
    }

    protected Map<String, String> deployEapPods(int replicas) throws Exception {
        // hack to create label
        Archive<?> archive = Archives.generateDummyWebArchive(configuration.getLabel() + ".war");

        RCContext context = WildFlySPIContainer.context(configuration, archive, replicas, readMountSecret(), configuration.getEapImageName());

        String rc = deployResourceContext(context);
        log.info(String.format("Deployed k8s resource [%s]: %s", replicas, rc));
        return context.getLabels();
    }

    @Override
    public void stop() throws LifecycleException {
        try {
//            try {
                getManagementClient().close();
//            } finally {
//                try {
//                    portFwd.close();
//                } catch (IOException ignored) {
//                }
//            }
        } finally {
            super.stop();
        }
    }

    protected ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException {
        try {
            String depoymentName = getArchiveDeployer().deploy(archive);
            log.info(String.format("EAP6 deployment: %s", depoymentName));
            return getProtocolMetaData(archive, labels);
        } catch (Exception e) {
            throw new DeploymentException("Cannot deploy in CE env.", e);
        }
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        try {
            getArchiveDeployer().undeploy(archive.getName());
        } finally {
            super.undeploy(archive);
        }
    }
}
