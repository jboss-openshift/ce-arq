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

import static org.jboss.arquillian.ce.utils.Strings.getSystemPropertyOrEnvVar;
import static org.jboss.arquillian.ce.utils.Strings.isNotNullOrEmpty;
import static org.jboss.arquillian.ce.utils.Strings.isNullOrEmpty;

import java.io.Serializable;
import java.util.Properties;

import org.jboss.arquillian.ce.api.ConfigurationHandle;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class Configuration implements ContainerConfiguration, ConfigurationHandle, Serializable {
    private static final long serialVersionUID = 1L;

    private org.arquillian.cube.kubernetes.api.Configuration cubeConfiguration;

    private String openshiftUsername = getSystemPropertyOrEnvVar("openshift.username", "guest");
    private String openshiftPassword = getSystemPropertyOrEnvVar("openshift.password", "guest");
    private String apiVersion = getSystemPropertyOrEnvVar("kubernetes.api.version", "v1");

    private String token = getSystemPropertyOrEnvVar("kubernetes.auth.token");
    private boolean trustCerts = Boolean.valueOf(getSystemPropertyOrEnvVar("kubernetes.trust.certs", "true"));

    private long startupTimeout = Integer.parseInt(getSystemPropertyOrEnvVar("arquillian.startup.timeout", "600")); // 10min ...
    private long httpClientTimeout = Integer.parseInt(getSystemPropertyOrEnvVar("arquillian.http.client.timeout", "120")); //default: 2 minutes

    public Properties getProperties() {
        Properties properties = new Properties();
        apply(properties);
        return properties;
    }

    protected void apply(Properties properties) {
        // namespace
        properties.put("kubernetes.namespace", cubeConfiguration.getNamespace());
        properties.put("namespace", cubeConfiguration.getNamespace());
        // api version
        properties.put("version", getApiVersion());
        properties.put("kubernetes.api.version", getApiVersion());
    }

    public void validate() throws ConfigurationException {
        if (cubeConfiguration == null)
            throw new ConfigurationException("CubeConfiguration is null");

        if (isNullOrEmpty(cubeConfiguration.getMasterUrl().toString()))
            throw new ConfigurationException("NULL master URL");

        if ((isNullOrEmpty(openshiftUsername) || isNullOrEmpty(openshiftPassword)) && isNullOrEmpty(token)) {
            throw new ConfigurationException("Missing OpenShift authentification -- username/password or token!");
        }
    }

    public String getKubernetesMaster() {
        return cubeConfiguration.getMasterUrl().toString();
    }

    public String getNamespace() {
        return cubeConfiguration.getNamespace();
    }

    public boolean hasOpenshiftBasicAuth() {
        return isNotNullOrEmpty(openshiftUsername) && isNotNullOrEmpty(openshiftPassword);
    }

    public String getOpenshiftUsername() {
        return openshiftUsername;
    }

    public void setOpenshiftUsername(String openshiftUsername) {
        this.openshiftUsername = openshiftUsername;
    }

    public String getOpenshiftPassword() {
        return openshiftPassword;
    }

    public void setOpenshiftPassword(String openshiftPassword) {
        this.openshiftPassword = openshiftPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isTrustCerts() {
        return trustCerts;
    }

    public void setTrustCerts(boolean trustCerts) {
        this.trustCerts = trustCerts;
    }

    public long getStartupTimeout() {
        return startupTimeout;
    }

    public void setStartupTimeout(long startupTimeout) {
        this.startupTimeout = startupTimeout;
    }

    public long getHttpClientTimeout() {
        return httpClientTimeout;
    }

    public void setHttpClientTimeout(long httpClientTimeout) {
        this.httpClientTimeout = httpClientTimeout;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public org.arquillian.cube.kubernetes.api.Configuration getCubeConfiguration() {
        return cubeConfiguration;
    }

    public void setCubeConfiguration(org.arquillian.cube.kubernetes.api.Configuration cubeConfiguration) {
        this.cubeConfiguration = cubeConfiguration;
    }
}
