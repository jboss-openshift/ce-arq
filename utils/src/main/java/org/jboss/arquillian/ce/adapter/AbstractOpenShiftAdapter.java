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

package org.jboss.arquillian.ce.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.arquillian.ce.resources.OpenShiftResourceHandle;
import org.jboss.arquillian.ce.utils.Configuration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractOpenShiftAdapter implements OpenShiftAdapter {
    protected final Configuration configuration;
    private Map<String, List<OpenShiftResourceHandle>> resourcesMap = new ConcurrentHashMap<>();

    protected AbstractOpenShiftAdapter(Configuration configuration) {
        this.configuration = configuration;
    }

    private void addResourceHandle(String resourcesKey, OpenShiftResourceHandle handle) {
        List<OpenShiftResourceHandle> list = resourcesMap.get(resourcesKey);
        if (list == null) {
            list = new ArrayList<>();
            resourcesMap.put(resourcesKey, list);
        }
        list.add(handle);
    }

    protected abstract OpenShiftResourceHandle createResourceFromStream(InputStream stream) throws IOException;

    public Object createResource(String resourcesKey, InputStream stream) throws IOException {
        OpenShiftResourceHandle resourceHandle = createResourceFromStream(stream);
        addResourceHandle(resourcesKey, resourceHandle);
        return resourceHandle;
    }

    public Object deleteResources(String resourcesKey) {
        List<OpenShiftResourceHandle> list = resourcesMap.remove(resourcesKey);
        if (list != null) {
            for (OpenShiftResourceHandle resource : list) {
                resource.delete();
            }
        }
        return list;
    }

    protected abstract OpenShiftResourceHandle createRoleBinding(String roleRefName, String userName);

    public Object addRoleBinding(String resourcesKey, String roleRefName, String userName) {
        OpenShiftResourceHandle handle = createRoleBinding(roleRefName, userName);
        addResourceHandle(resourcesKey, handle);
        return handle;
    }
    
    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
