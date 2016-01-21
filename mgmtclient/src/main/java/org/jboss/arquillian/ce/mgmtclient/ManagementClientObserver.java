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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.jboss.arquillian.ce.utils.ArchiveHolder;
import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.as.network.NetworkUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.container.ManifestContainer;

/**
 * From ArquillianServiceDeployer
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ManagementClientObserver {
    public void observe(@Observes BeforeDeploy event, Container container, ArchiveHolder archiveHolder) throws IOException {
        final Archive<?> archive = archiveHolder.getArchive();
        final Map<String, String> props = container.getContainerConfiguration().getContainerProperties();

        //write the management connection props to the archive, so we can access them from the server
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(props.get("managementPort"));
            out.writeObject(NetworkUtils.formatPossibleIpv6Address(props.get("managementAddress")));
        }

        if (archive instanceof ManifestContainer) {
            ManifestContainer manifestContainer = ManifestContainer.class.cast(archive);
            manifestContainer.addAsManifestResource(new ByteArrayAsset(bytes.toByteArray()), "org.jboss.as.managementConnectionProps");
        }
    }
}
