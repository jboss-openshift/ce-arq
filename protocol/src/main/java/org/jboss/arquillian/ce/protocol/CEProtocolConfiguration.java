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

import org.jboss.arquillian.protocol.servlet.ServletProtocolConfiguration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CEProtocolConfiguration extends ServletProtocolConfiguration {
    private ProtocolConfiguration configuration = new ProtocolConfiguration();

    private String kubernetesMaster;
    private String apiVersion;
    private String namespace;

    public String getKubernetesMaster() {
        if (kubernetesMaster != null) {
            return kubernetesMaster;
        }
        return configuration.getKubernetesMaster();
    }

    public String getApiVersion() {
        if (apiVersion != null) {
            return apiVersion;
        }
        return configuration.getApiVersion();
    }

    public String getNamespace() {
        if (namespace != null) {
            return namespace;
        }
        return configuration.getNamespace();
    }

    public void setKubernetesMaster(String kubernetesMaster) {
        this.kubernetesMaster = kubernetesMaster;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
