/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other
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

package org.jboss.arquillian.ce.httpclient;

import java.io.IOException;

import org.apache.http.impl.client.CloseableHttpClient;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class HttpClientImpl implements HttpClient {
    private CloseableHttpClient client;

    public HttpClientImpl(CloseableHttpClient client) {
        this.client = client;
    }

    public HttpResponse execute(HttpRequest request) throws IOException, InterruptedException {
        return execute(request, new HttpClientExecuteOptions.Builder().build());
    }

    public HttpResponse execute(HttpRequest request, HttpClientExecuteOptions options) throws IOException, InterruptedException {
        HttpResponse response = null;

        for (int i = 0; i < options.getTries(); i++) {
            try {
                response = new HttpResponseImpl(client.execute(HttpRequestImpl.class.cast(request).unwrap()));
                if (options.getDesiredStatusCode() == -1 || response.getResponseCode() == options.getDesiredStatusCode())
                    break;
                System.err.println(String.format("Execute error: Got code %d, expected %d. Trying again in %d seconds",
                        response.getResponseCode(), options.getDesiredStatusCode(), options.getDelay()));
            } catch (IOException e) {
                if (i + 1 == options.getTries())
                    throw e;
                System.err.println(String.format("Execute error: %s. Trying again in %d seconds", e, options.getDelay()));
            }
            Thread.sleep(options.getDelay() * 1000);
        }

        return response;
    }

    public void close() throws IOException {
        client.close();
    }
}
