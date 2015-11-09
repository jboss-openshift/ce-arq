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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.SSLContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import com.ning.http.client.cookie.Cookie;
import org.jboss.arquillian.ce.api.ConfigurationHandle;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractProxy<P> implements Proxy {
    private static final String PROXY_URL = "%s/api/%s/namespaces/%s/pods/%s:8080/proxy%s";

    protected final ConfigurationHandle configuration;
    private final Map<String, Cookie> cookieMap = new HashMap<>();

    public AbstractProxy(ConfigurationHandle configuration) {
        this.configuration = configuration;
    }

    public void setDefaultSSLContext() {
        SSLContext.setDefault(getHttpClient().getConfig().getSSLContext());
    }

    private String url(String host, String version, String namespace, String podName, String path, String parameters) {
        String url = String.format(PROXY_URL, host, version, namespace, podName, path);
        return (parameters != null && parameters.length() > 0) ? url + "?" + parameters : url;
    }

    public String url(String podName, String path, String parameters) {
        String url = String.format(PROXY_URL, configuration.getKubernetesMaster(), configuration.getApiVersion(), configuration.getNamespace(), podName, path);
        return (parameters != null && parameters.length() > 0) ? url + "?" + parameters : url;
    }

    protected abstract List<P> getPods(Map<String, String> labels);

    protected abstract String getName(P pod);

    protected abstract boolean isReady(P pod);

    public String url(Map<String, String> labels, String path, String parameters) {
        return url(labels, 0, path, parameters);
    }

    public String url(Map<String, String> labels, int index, String path, String parameters) {
        List<P> items = getPods(labels);
        if (index >= items.size()) {
            throw new IllegalStateException(String.format("Not enough pods (%s) to invoke pod index %s!", items.size(), index));
        }
        String pod = getName(items.get(index));

        return url(pod, path, parameters);
    }

    public Set<String> getReadyPods(Map<String, String> labels) {
        Set<String> names = new TreeSet<>();
        List<P> pods = getPods(labels);
        for (P pod : pods) {
            if (isReady(pod)) {
                names.add(getName(pod));
            }
        }
        return names;
    }

    public String findPod(Map<String, String> labels) {
        return findPod(labels, 0);
    }

    public String findPod(Map<String, String> labels, int index) {
        List<P> items = getPods(labels);
        if (index >= items.size()) {
            throw new IllegalStateException(String.format("Not enough pods (%s) to invoke pod index %s!", items, index));
        } else {
            return getName(items.get(index));
        }
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

    public synchronized InputStream post(Map<String, String> labels, int pod, String path) throws Exception {
        String url = url(
            labels,
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
