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

package org.jboss.arquillian.ce.runinpod;

import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class RunInPodContainer implements DeployableContainer<RunInPodConfiguration> {
    protected final DeployableContainer<? extends Configuration> delegate;
    protected final Archive<?> archive;

    public RunInPodContainer(DeployableContainer<? extends Configuration> delegate, Archive<?> archive) {
        this.delegate = delegate;
        this.archive = archive;
    }

    public void deploy() throws DeploymentException {
        deploy(archive);
    }

    public void undeploy() throws DeploymentException {
        undeploy(archive);
    }

    public Class<RunInPodConfiguration> getConfigurationClass() {
        return RunInPodConfiguration.class;
    }

    public void setup(RunInPodConfiguration c) {
        // should be already invoked on delegate
    }

    public ProtocolDescription getDefaultProtocol() {
        return delegate.getDefaultProtocol();
    }

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        return delegate.deploy(archive);
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
        delegate.undeploy(archive);
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
        delegate.deploy(descriptor);
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
        delegate.undeploy(descriptor);
    }
}
