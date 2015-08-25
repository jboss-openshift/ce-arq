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

import org.jboss.arquillian.ce.api.Client;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientProvider implements ResourceProvider {

    @Inject
    Instance<Client> clientInstance;

    public boolean canProvide(Class<?> type) {
        return Client.class.isAssignableFrom(type);
    }

    public Object lookup(ArquillianResource resource, Annotation... qualifiers) {
        Client client = clientInstance.get();
        if (client == null) {
            throw new IllegalStateException("Unable to inject client into test.");
        }
        return client;
    }

}
