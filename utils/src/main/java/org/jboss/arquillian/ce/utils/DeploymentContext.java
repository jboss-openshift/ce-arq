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

package org.jboss.arquillian.ce.utils;

import java.util.Collections;
import java.util.Map;

import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class DeploymentContext {
    private final static String DEPLOYMENT_ARCHIVE_NAME_KEY = "deploymentArchiveName";

    private final Archive<?> archive;
    private Map<String, String> labels;
    private Configuration configuration;

    public DeploymentContext(Archive<?> archive, Map<String, String> labels, Configuration configuration) {
        this.archive = archive;
        this.labels = labels;
        this.configuration = configuration;
    }

    public static DeploymentContext getDeploymentContext(ProtocolMetaData pmd) {
        return pmd.getContexts(DeploymentContext.class).iterator().next();
    }

    public static Map<String, String> getDeploymentLabels(Archive<?> archive) {
        return Collections.singletonMap(DEPLOYMENT_ARCHIVE_NAME_KEY, archive.getName());
    }

    public Archive<?> getArchive() {
        return archive;
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}
