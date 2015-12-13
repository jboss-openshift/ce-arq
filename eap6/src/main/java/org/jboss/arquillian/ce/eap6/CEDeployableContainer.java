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
import java.util.Map;

import org.jboss.arquillian.ce.spi.WildFlySPIContainer;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.ce.utils.Archives;
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
import org.jboss.as.controller.client.ModelControllerClient;
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
            Map<String, String> labels = deployEapPods(replicas);
            delay(labels, replicas);

            String address = proxy.url(labels, 0, configuration.getMgmtPort(), "", null);
            int port = configuration.getMgmtPort();

            ModelControllerClient modelControllerClient = ModelControllerClient.Factory.create(
                address,
                port,
                null,
                proxy.getSSLContext());

            ManagementClient client = new ManagementClient(modelControllerClient, address, port);
            managementClient.set(client);

            ArchiveDeployer deployer = new ArchiveDeployer(modelControllerClient);
            archiveDeployer.set(deployer);
        } catch (Exception e) {
            throw new LifecycleException("Error setting up EAP6 pod.", e);
        }
    }

    protected Map<String, String> deployEapPods(int replicas) throws Exception {
        // hack to create label
        Archive<?> archive = Archives.generateDummyWebArchive(configuration.getLabel() + ".war");

        RCContext context = WildFlySPIContainer.context(configuration, archive, replicas, configuration.getEapImageName());

        String rc = deployResourceContext(context);
        log.info(String.format("Deployed k8s resource [%s]: %s", replicas, rc));
        return context.getLabels();
    }

    @Override
    public void stop() throws LifecycleException {
        try {
            getManagementClient().close();
        } finally {
            super.stop();
        }
    }

    protected ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException {
        String runtimeName = getArchiveDeployer().deploy(archive);
        return getManagementClient().getProtocolMetaData(runtimeName);
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
