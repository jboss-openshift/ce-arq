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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.proxy.Proxy;
import org.jboss.arquillian.ce.resources.OpenShiftResourceHandle;
import org.jboss.arquillian.ce.utils.Checker;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Containers;
import org.jboss.arquillian.ce.utils.DeploymentContext;
import org.jboss.arquillian.ce.utils.Operator;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.core.api.Instance;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractOpenShiftAdapter implements OpenShiftAdapter {
    protected final Logger log = Logger.getLogger(getClass().getName());

    protected final Configuration configuration;
    private Map<String, List<OpenShiftResourceHandle>> resourcesMap = new ConcurrentHashMap<>();
    private Proxy proxy;
    private Instance<ProtocolMetaData> pmdInstance;

    protected AbstractOpenShiftAdapter(Configuration configuration) {
        this.configuration = configuration;
    }

    public void setProtocolMetaData(Instance<ProtocolMetaData> pmd) {
        this.pmdInstance = pmd;
    }

    protected abstract Proxy createProxy();

    public String url(String podName, int port, String path, String parameters) {
        return getProxy().url(podName, port, path, parameters);
    }

    public InputStream execute(String podName, int port, String path) throws Exception {
        return getProxy().post(podName, port, path);
    }

    public InputStream execute(int pod, int port, String path) throws Exception {
        ProtocolMetaData pmd = pmdInstance.get();
        if (pmd != null) {
            Map<String, String> labels = DeploymentContext.getDeploymentContext(pmd).getLabels();
            return execute(labels, pod, port, path);
        } else {
            throw new IllegalStateException("No ProtocolMetaData set!");
        }
    }

    public InputStream execute(Map<String, String> labels, int pod, int port, String path) throws Exception {
        return getProxy().post(labels, pod, port, path);
    }

    public synchronized Proxy getProxy() {
        if (proxy == null) {
            proxy = createProxy();
        }
        return proxy;
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

    protected abstract Map<String, String> getLabels(String prefix) throws Exception;

    public void replacePods(String prefix, int size, final int replicas) throws Exception {
        final Map<String, String> labels = getLabels(prefix);

        final Set<String> deleted = new HashSet<>();
        for (String pod : getProxy().getReadyPods(labels)) {
            if (size <= 0) {
                break;
            }
            deleted.add(pod);
            deletePod(pod, -1); // use default grace period?
            size--;
        }

        Containers.delay(configuration.getStartupTimeout(), 4000L, new PodCountChecker(labels, Operator.EQUAL, replicas) {
            @Override
            protected Set<String> getReadyPods() {
                Set<String> pods = super.getReadyPods();
                pods.removeAll(deleted);
                return pods;
            }
        });
    }

    public List<String> getPods() throws Exception {
        return getPods(null);
    }

    public void delay(final Map<String, String> labels, final int replicas, final Operator op) throws Exception {
        Containers.delay(configuration.getStartupTimeout(), 4000L, new PodCountChecker(labels, op, replicas));
    }

    private class PodCountChecker implements Checker {
        private final Map<String, String> labels;
        private final Operator op;
        private final int replicas;

        public PodCountChecker(Map<String, String> labels, Operator op, int replicas) {
            this.labels = labels;
            this.op = op;
            this.replicas = replicas;
        }

        public boolean check() {
            Set<String> pods = getReadyPods();
            boolean result = op.op(pods.size(), replicas);
            if (result) {
                log.info(String.format("Condition satisfied: number of pod(s) matching labels: %s is %s %s (pods: %s)", labels, op, replicas, pods));
            }
            return result;
        }

        protected Set<String> getReadyPods() {
            return getProxy().getReadyPods(labels);
        }

        @Override
        public String toString() {
            return String.format("Number of pod(s) matching labels: %s is %s %s", labels, op, replicas);
        }
    }
}
