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

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import org.jboss.arquillian.ce.utils.AbstractOpenShiftAdapter;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Proxy;
import org.jboss.arquillian.ce.utils.ProxyFactory;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.protocol.servlet.ServletMethodExecutor;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ProxyURLProvider implements ResourceProvider {
    @Inject
    Instance<Configuration> configurationInstance;

    @Inject
    Instance<ProtocolMetaData> protocolMetaDataInstance;

    private Proxy proxy;

    private synchronized Proxy getProxy() {
        if (proxy == null) {
            proxy = ProxyFactory.getProxy(configurationInstance.get());
            proxy.setDefaultSSLContext();
        }
        return proxy;
    }

    private String getContext() {
        ProtocolMetaData pmd = protocolMetaDataInstance.get();

        Collection<Archive> archives = pmd.getContexts(Archive.class);
        if (archives.size() > 0) {
            Archive top = archives.iterator().next();
            if (top instanceof EnterpriseArchive) {
                return "";
            }
        }

        for (HTTPContext httpContext : pmd.getContexts(HTTPContext.class)) {
            for (Servlet servlet : httpContext.getServlets()) {
                if (ServletMethodExecutor.ARQUILLIAN_SERVLET_NAME.equals(servlet.getName())) {
                    return servlet.getContextRoot();
                }
            }
        }
        throw new IllegalStateException("Cannot read servlet context!");
    }

    public boolean canProvide(Class<?> type) {
        return URL.class.isAssignableFrom(type);
    }

    public Object lookup(ArquillianResource arquillianResource, Annotation... annotations) {
        try {
            Archive<?> archive = protocolMetaDataInstance.get().getContexts(Archive.class).iterator().next();
            Map<String, String> labels = AbstractOpenShiftAdapter.getDeploymentLabels(archive);

            Configuration c = configurationInstance.get();

            String context = getContext();
            if (context.endsWith("/") == false) {
                context = context + "/";
            }

            String podName = getProxy().findPod(labels, c.getNamespace());

            String spec = getProxy().url(c.getKubernetesMaster(), c.getApiVersion(), c.getNamespace(), podName, context, null);
            return new URL(spec);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
