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

package org.jboss.arquillian.ce.mgmtclient;

import static org.jboss.as.arquillian.container.Authentication.getCallbackHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.annotation.Annotation;
import java.net.URL;

import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.jboss.arquillian.test.spi.event.suite.AfterSuite;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * resource provider that allows the ManagementClient to be injected inside the container.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class InContainerManagementClientProvider implements ResourceProvider {

    private static ManagementClient current;

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider#canProvide(Class)
     */
    @Override
    public boolean canProvide(final Class<?> type) {
        return type.isAssignableFrom(ManagementClient.class);
    }

    @Override
    public synchronized Object lookup(final ArquillianResource arquillianResource, final Annotation... annotations) {
        if (current != null) {
            return current;
        }
        final URL resourceUrl = getClass().getClassLoader().getResource("META-INF/org.jboss.as.managementConnectionProps");
        if (resourceUrl != null) {
            InputStream in = null;
            String managementPort;
            String address;
            try {
                in = resourceUrl.openStream();
                ObjectInputStream inputStream = new ObjectInputStream(in);
                managementPort = (String) inputStream.readObject();
                address = (String) inputStream.readObject();
                if (address == null) {
                    address = "localhost";
                }
                if (managementPort == null) {
                    managementPort = "9999";
                }
                final int port = Integer.parseInt(managementPort);
                ModelControllerClient modelControllerClient = ModelControllerClient.Factory.create(
                    address,
                    port,
                    getCallbackHandler());
                current = new ManagementClient(modelControllerClient, address, port);

            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        //ignore
                    }
                }
            }

        }
        return current;
    }

    public synchronized void cleanUp(@Observes AfterSuite afterSuite) {
        if (current != null) {
            current.close();
            current = null;
        }
    }
}
