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

package org.jboss.arquillian.ce.openshift;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.OkHttpClientUtils;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class HttpClientCreator {
    static class CeOkHttpClient extends OkHttpClient {
        private SSLContext sslContext;

        SSLContext getSslContext() {
            return sslContext;
        }

        private void setSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
        }
    }

    static CeOkHttpClient createHttpClient(final Configuration configuration) {
        try {
            CeOkHttpClient httpClient = new CeOkHttpClient();

            // Follow any redirects
            httpClient.setFollowRedirects(true);
            httpClient.setFollowSslRedirects(true);

            KeyManager[] keyManagers = null; // TODO
            TrustManager[] trustManagers = null; // TODO

            if (configuration.isTrustCerts()) {
                httpClient.setHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }
                });

                trustManagers = new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String s) {
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String s) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            httpClient.setSslSocketFactory(sslContext.getSocketFactory());
            httpClient.setSslContext(sslContext); // impl detail

            httpClient.interceptors().add(new Interceptor() {
                @Override
                public Response intercept(Interceptor.Chain chain) throws IOException {
                    Request authReq = chain.request().newBuilder().addHeader("Authorization", "Bearer " + configuration.getToken()).build();
                    return chain.proceed(authReq);
                }
            });

            httpClient.setConnectTimeout(10, TimeUnit.SECONDS);
            httpClient.setReadTimeout(10, TimeUnit.SECONDS);

            OkHttpClientUtils.applyCookieHandler(httpClient);

            return httpClient;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
