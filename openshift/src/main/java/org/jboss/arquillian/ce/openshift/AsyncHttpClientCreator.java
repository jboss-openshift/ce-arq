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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import com.ning.http.client.filter.RequestFilter;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import org.jboss.arquillian.ce.utils.Configuration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class AsyncHttpClientCreator {
    static AsyncHttpClient createHttpClient(final Configuration configuration) {
        try {
            AsyncHttpClientConfig.Builder clientConfigBuilder = new AsyncHttpClientConfig.Builder();
            clientConfigBuilder.setEnabledProtocols(new String[]{"TLSv1.2"});

            // Follow any redirects
            clientConfigBuilder.setFollowRedirect(true);

            // Should we disable all server certificate checks?
            clientConfigBuilder.setAcceptAnyCertificate(true);

            // auth
            clientConfigBuilder.addRequestFilter(new RequestFilter() {
                @Override
                public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
                    ctx.getRequest().getHeaders().add("Authorization", "Bearer " + configuration.getToken());
                    return ctx;
                }
            });

            clientConfigBuilder.setRequestTimeout(10 * 1000);

            NettyAsyncHttpProviderConfig nettyConfig = new NettyAsyncHttpProviderConfig();
            nettyConfig.setWebSocketMaxFrameSize(65536);
            clientConfigBuilder.setAsyncHttpClientProviderConfig(nettyConfig);

            return new AsyncHttpClient(clientConfigBuilder.build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
