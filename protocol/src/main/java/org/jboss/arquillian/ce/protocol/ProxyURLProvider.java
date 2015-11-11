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
import java.util.Map;

import org.jboss.arquillian.ce.utils.DeploymentContext;
import org.jboss.arquillian.ce.proxy.Proxy;
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
    Instance<ProtocolMetaData> protocolMetaDataInstance;

    private String getContext() {
        ProtocolMetaData pmd = protocolMetaDataInstance.get();
        DeploymentContext deploymentContext = DeploymentContext.getDeploymentContext(pmd);

        Archive top = deploymentContext.getArchive();
        if (top instanceof EnterpriseArchive) {
            return "";
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
            DeploymentContext deploymentContext = DeploymentContext.getDeploymentContext(protocolMetaDataInstance.get());
            Map<String, String> labels = deploymentContext.getLabels();

            String context = getContext();
            if (context.endsWith("/") == false) {
                context = context + "/";
            }

            Proxy proxy = deploymentContext.getProxy();
            proxy.setDefaultSSLContext(); // URL instance needs this

            String podName = proxy.findPod(labels);

            String spec = proxy.url(podName, context, null);
            return new URL(spec);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
