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

package org.jboss.arquillian.ce.mgmtclient;

import org.jboss.arquillian.ce.utils.ArchiveHolder;
import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.annotation.SuiteScoped;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.Authentication;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.arquillian.container.NetworkUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class InContainerManagementClientAppender implements AuxiliaryArchiveAppender {
    @Inject
    @SuiteScoped
    private InstanceProducer<ArchiveHolder> archiveHolderInstance;

    public Archive<?> createAuxiliaryArchive() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "incontainermgmtclient.jar")
            .addClasses(ServerSetup.class, ServerSetupTask.class, ManagementClient.class, Authentication.class, NetworkUtils.class)
            .addClass(InContainerManagementClientProvider.class)
            .addClass(InContainerManagementClientExtension.class)
            .addAsServiceProviderAndClasses(RemoteLoadableExtension.class, InContainerManagementClientExtension.class);

        archiveHolderInstance.set(new ArchiveHolder(jar));

        return jar;
    }
}
