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

package org.jboss.arquillian.ce.utils;

import java.io.Serializable;
import java.util.Properties;

import org.jboss.arquillian.container.spi.ConfigurationException;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class Configuration implements Serializable {
    private static final long serialVersionUID = 1L;

    private String kubernetesMaster = System.getenv("KUBERNETES_MASTER");
    private String dockerUrl = System.getenv("DOCKER_URL");

    /**
     * Apply configuration to resolver properties.
     */
    @SuppressWarnings("UnusedParameters")
    public void apply(Properties properties) {
    }

    public void validate() throws ConfigurationException {
        if (kubernetesMaster == null) {
            throw new ConfigurationException("Null Kubernetes master!");
        }
        if (dockerUrl == null) {
            throw new ConfigurationException("Null Docker url!");
        }
    }

    public String getKubernetesMaster() {
        return kubernetesMaster;
    }

    public void setKubernetesMaster(String kubernetesMaster) {
        this.kubernetesMaster = kubernetesMaster;
    }

    public String getDockerUrl() {
        return dockerUrl;
    }

    public void setDockerUrl(String dockerUrl) {
        this.dockerUrl = dockerUrl;
    }
}
