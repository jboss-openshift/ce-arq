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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.com.ning.http.client.AsyncHttpClient;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Proxy {
    private static final String PROXY_URL = "%s/api/%s/namespaces/%s/pods/%s/proxy%s?port=8080%s";

    private KubernetesClient client;

    public Proxy(String kubernetesMaster) {
        Config config = new ConfigBuilder().withMasterUrl(kubernetesMaster).build();
        this.client = new DefaultKubernetesClient(config);
    }

    public Proxy(KubernetesClient client) {
        this.client = client;
    }

    public AsyncHttpClient getHttpClient() {
        return client.getHttpClient();
    }

    public String url(String host, String version, String namespace, int index, String path, String parameters) {
        List<Pod> items = client.pods().inNamespace(namespace).list().getItems();
        if (index >= items.size()) {
            throw new IllegalStateException(String.format("Not enough pods (%s) to invoke pod %s!", items.size(), index));
        }
        String pod = items.get(index).getMetadata().getName();

        return String.format(PROXY_URL, host, version, namespace, pod, path, parameters);
    }

    public List<String> urls(String host, String namespace, String version, String path) {
        List<Pod> items = client.pods().inNamespace(namespace).list().getItems();

        List<String> urls = new ArrayList<>();
        for (Pod pod : items) {
            urls.add(String.format(PROXY_URL, host, version, namespace, pod, path, ""));
        }
        return urls;
    }
}
