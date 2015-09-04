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

package org.jboss.arquillian.ce.protocol;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import io.fabric8.kubernetes.client.internal.com.ning.http.client.AsyncHttpClient;
import io.fabric8.kubernetes.client.internal.com.ning.http.client.Response;
import io.fabric8.kubernetes.client.internal.com.ning.http.client.cookie.Cookie;
import org.jboss.arquillian.ce.api.Client;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Proxy;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientCreator {
    private static final Logger log = Logger.getLogger(ClientCreator.class.getName());

    @Inject
    @ApplicationScoped
    InstanceProducer<Client> clientInstanceProducer;

    public synchronized void createClient(final @Observes Configuration configuration) {
        if (clientInstanceProducer.get() == null) {
            final Proxy proxy = new Proxy(configuration.getKubernetesMaster());
            Client client = new Client() {
                private Map<String, Cookie> cookieMap = new TreeMap<>();

                @Override
                public synchronized InputStream execute(int pod, String path) throws Exception {
                    String url = proxy.url(
                        configuration.getKubernetesMaster(),
                        configuration.getApiVersion(),
                        configuration.getNamespace(),
                        pod,
                        path,
                        ""
                    );

                    log.info(String.format("Invoking url: %s", url));

                    AsyncHttpClient httpClient = proxy.getHttpClient();
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
            };
            clientInstanceProducer.set(client);
        }
    }

}
