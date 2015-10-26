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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import com.ning.http.client.cookie.Cookie;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractProxy<P> implements Proxy {
    private static final String PROXY_URL = "%s/api/%s/namespaces/%s/pods/%s:8080/proxy%s";

    private final Map<String, Cookie> cookieMap = new HashMap<>();

    public void setDefaultSSLContext() {
        SSLContext.setDefault(getHttpClient().getConfig().getSSLContext());
    }

    public String url(String host, String version, String namespace, String podName, String path, String parameters) {
        String url = String.format(PROXY_URL, host, version, namespace, podName, path);
        return (parameters != null && parameters.length() > 0) ? url + "?" + parameters : url;
    }

    public Map.Entry<Integer, String> findPod(Map<String, String> labels, Configuration configuration, String path) {
        String host = configuration.getKubernetesMaster();
        String version = configuration.getApiVersion();
        String namespace = configuration.getNamespace();
        return findPod(labels, host, version, namespace, path);
    }

    protected abstract List<P> getPods(String namespace, Map<String, String> labels);

    protected abstract String getName(P pod);

    public String url(Map<String, String> labels, String host, String version, String namespace, int index, String path, String parameters) {
        List<P> items = getPods(namespace, labels);
        if (index >= items.size()) {
            throw new IllegalStateException(String.format("Not enough pods (%s) to invoke pod index %s!", items.size(), index));
        }
        String pod = getName(items.get(index));

        return url(host, version, namespace, pod, path, parameters);
    }

    public List<String> urls(Map<String, String> labels, String host, String namespace, String version, String path) {
        List<P> items = getPods(namespace, labels);

        List<String> urls = new ArrayList<>();
        for (P pod : items) {
            String podName = getName(pod);
            urls.add(url(host, version, namespace, podName, path, null));
        }
        return urls;
    }

    public int podsSize(Map<String, String> labels, String namespace) {
        return getPods(namespace, labels).size();
    }

    public Map.Entry<Integer, String> findPod(Map<String, String> labels, String host, String version, String namespace, String path) {
        List<P> items = getPods(namespace, labels);
        int i;
        for (i = 0; i < items.size(); i++) {
            String podName = getName(items.get(i));
            String url = url(host, version, namespace, podName, path, null);
            if (status(url) == 200) {
                return new AbstractMap.SimpleEntry<>(i, podName);
            }
        }
        throw new IllegalArgumentException(String.format("No matching pod: %s, path: %s", items, path));
    }

    protected abstract AsyncHttpClient getHttpClient();

    public <T> T post(String url, Class<T> returnType, Object requestObject) throws Exception {
        final AsyncHttpClient httpClient = getHttpClient();

        AsyncHttpClient.BoundRequestBuilder builder = httpClient.preparePost(url);

        if (requestObject != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(requestObject);
                oos.flush();
            } catch (Exception e) {
                throw new RuntimeException("Error sending request Object, " + requestObject, e);
            }
            builder.setBody(baos.toByteArray());
        }

        ListenableFuture<Response> future = builder.execute();
        Response response = future.get();

        int responseCode = response.getStatusCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            Object o;
            try (ObjectInputStream ois = new ObjectInputStream(response.getResponseBodyAsStream())) {
                o = ois.readObject();
            }

            if (returnType.isInstance(o) == false) {
                throw new IllegalStateException("Error reading results, expected a " + returnType.getName() + " but got " + o);
            }

            return returnType.cast(o);
        } else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            return null;
        } else if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
            throw new IllegalStateException("Error launching test at " + url + ". Got " + responseCode + " (" + response.getStatusText() + ")");
        }

        return null; // TODO
    }

    public synchronized InputStream post(Map<String, String> labels, Configuration configuration, int pod, String path) throws Exception {
        String url = url(
            labels,
            configuration.getKubernetesMaster(),
            configuration.getApiVersion(),
            configuration.getNamespace(),
            pod,
            path,
            null
        );

        AsyncHttpClient httpClient = getHttpClient();
        AsyncHttpClient.BoundRequestBuilder builder = httpClient.preparePost(url);

        Cookie match = null;
        for (Map.Entry<String, Cookie> entry : cookieMap.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                match = entry.getValue();
                break;
            }
        }
        if (match != null) {
            builder.addCookie(match);
        }

        Response response = builder.execute().get();

        // handle cookies
        List<Cookie> cookies = response.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equalsIgnoreCase("JSESSIONID")) {
                    cookieMap.put(cookie.getPath(), cookie);
                }
            }
        }

        return response.getResponseBodyAsStream();
    }

    public int status(String url) {
        try {
            AsyncHttpClient httpClient = getHttpClient();
            AsyncHttpClient.BoundRequestBuilder builder = httpClient.preparePost(url);
            Response response = builder.execute().get();
            return response.getStatusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
