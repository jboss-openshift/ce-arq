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

import java.util.List;
import java.util.Map;

import com.ning.http.client.AsyncHttpClient;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.NoopSSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IPod;
import org.jboss.arquillian.ce.utils.AbstractProxy;
import org.jboss.arquillian.ce.utils.Configuration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class NativeProxy extends AbstractProxy<IPod> {
    private final IClient client;
    private final AsyncHttpClient httpClient;

    public NativeProxy(Configuration configuration) {
        this.client = new ClientFactory().create(configuration.getKubernetesMaster(), new NoopSSLCertificateCallback());
        this.client.setAuthorizationStrategy(new TokenAuthorizationStrategy(configuration.getToken()));
        this.httpClient = createHttpClient(configuration);
    }

    protected AsyncHttpClient createHttpClient(Configuration configuration) {
        return AsyncHttpClientCreator.createHttpClient(configuration);
    }

    protected AsyncHttpClient getHttpClient() {
        return httpClient;
    }

    protected List<IPod> getPods(String namespace, Map<String, String> labels) {
        return client.list(ResourceKind.POD, namespace, labels);
    }

    protected String getName(IPod pod) {
        return pod.getName();
    }
}
