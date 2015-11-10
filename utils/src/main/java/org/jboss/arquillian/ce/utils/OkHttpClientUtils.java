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

import java.io.IOException;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.squareup.okhttp.OkHttpClient;

/**
 * Handle OkHttpClient.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OkHttpClientUtils {
    private static final SimpleCookieHandler COOKIE_HANDLER = new SimpleCookieHandler();

    public static void applyCookieHandler(OkHttpClient httpClient) {
        COOKIE_HANDLER.clear(); // reset
        httpClient.setCookieHandler(COOKIE_HANDLER);
    }

    /**
     * Just copy cookies based on proxy path.
     */
    private static class SimpleCookieHandler extends CookieHandler {
        private static final String _PROXY = "/proxy";
        private Map<String, List<HttpCookie>> cookiesMap = new HashMap<>();

        private void clear() {
            cookiesMap.clear();
        }

        private static String path(URI uri) {
            String path = uri.getPath();
            int p = path.indexOf(_PROXY);
            return path.substring(p + _PROXY.length());
        }

        public synchronized Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
            String path = path(uri);
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, List<HttpCookie>> entry : cookiesMap.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    for (HttpCookie cookie : entry.getValue()) {
                        list.add(cookie.toString());
                    }
                }
            }
            return list.isEmpty() ? Collections.<String, List<String>>emptyMap() : Collections.singletonMap("Cookie", list);
        }

        public synchronized void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                String headerKey = entry.getKey();
                if (headerKey.equalsIgnoreCase("Set-Cookie") || headerKey.equalsIgnoreCase("Set-Cookie2")) {
                    for (String headerValue : entry.getValue()) {
                        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
                            List<HttpCookie> cookies = cookiesMap.get(cookie.getPath());
                            if (cookies == null) {
                                cookies = new ArrayList<>();
                                cookiesMap.put(cookie.getPath(), cookies);
                            }
                            cookies.add(cookie);
                        }
                    }
                }
            }
        }
    }
}
