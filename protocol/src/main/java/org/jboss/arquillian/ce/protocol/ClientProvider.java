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
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.api.Client;
import org.jboss.arquillian.ce.utils.DeploymentContext;
import org.jboss.arquillian.ce.proxy.Proxy;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientProvider implements ResourceProvider {
    private static final Logger log = Logger.getLogger(ClientProvider.class.getName());

    @Inject
    Instance<ProtocolMetaData> protocolMetaDataInstance;

    public boolean canProvide(Class<?> type) {
        return Client.class.isAssignableFrom(type);
    }

    public Object lookup(ArquillianResource resource, Annotation... qualifiers) {
        Client client = new ClientImpl();
        log.info(String.format("Providing client [%s] instance.", client));
        return client;
    }

    private class ClientImpl implements Client {
        public synchronized InputStream execute(int pod, String path) throws Exception {
            log.info(String.format("Invoking pod #%s for path '%s'", pod, path));

            DeploymentContext context = DeploymentContext.getDeploymentContext(protocolMetaDataInstance.get());
            Map<String, String> labels = context.getLabels();
            Proxy proxy = context.getProxy();

            return proxy.post(labels, pod, 8080, path);
        }
    }
}
