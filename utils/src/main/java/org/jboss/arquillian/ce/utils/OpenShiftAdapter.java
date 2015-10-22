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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public interface OpenShiftAdapter extends Closeable, RegistryLookup {
    String DEPLOYMENT_ARCHIVE_NAME_KEY = "deploymentArchiveName";

    void prepare(Archive<?> archive);

    void reset(Archive<?> archive);

    File getDir(Archive<?> archive);

    Proxy createProxy();

    File exportAsZip(File dir, Archive<?> deployment);

    File exportAsZip(File dir, Archive<?> deployment, String name);

    String buildAndPushImage(DockerFileTemplateHandler dth, InputStream dockerfileTemplate, Archive deployment, Properties properties) throws IOException;

    Object createProject(String namespace);

    boolean deleteProject(String namespace);

    String deployReplicationController(String name, Map<String, String> labels, String imageName, List<Port> ports, int replicas, HookType hookType, String preStopPath, boolean ignorePreStop) throws Exception;

    Object processTemplateAndCreateResources(String name, String templateURL, String namespace, List<ParamValue> values) throws Exception;

    Object deleteTemplate(String name, String namespace) throws Exception;

    Object getService(String namespace, String serviceName);

    void cleanReplicationControllers(String... ids) throws Exception;

    void cleanPods(Map<String, String> labels) throws Exception;
}
