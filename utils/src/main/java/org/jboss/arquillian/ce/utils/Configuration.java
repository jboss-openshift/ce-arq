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

import java.io.Serializable;
import java.util.Properties;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class Configuration implements ContainerConfiguration, Serializable {
    private static final long serialVersionUID = 1L;

    private String kubernetesMaster = getSystemPropertyOrEnvVar("kubernetes.master");
    private String dockerUrl = getSystemPropertyOrEnvVar("docker.url");

    private String apiVersion = getSystemPropertyOrEnvVar("kubernetes.api.version", "v1");
    private String namespace = getSystemPropertyOrEnvVar("kubernetes.namespace", "default");

    private String fromParent = getSystemPropertyOrEnvVar("from.parent");
    private String deploymentDir = getSystemPropertyOrEnvVar("deployment.dir");

    private String registryNamespace = getSystemPropertyOrEnvVar("kubernetes.registry.namespace", "default");
    private String registryServiceName = getSystemPropertyOrEnvVar("kubernetes.registry.service.name", "docker-registry");

    private String preStopHookType = getSystemPropertyOrEnvVar("kubernetes.container.pre-stop-hook-type", HookType.HTTP_GET.name());
    private String preStopPath = getSystemPropertyOrEnvVar("kubernetes.container.pre-stop", "/pre-stop/_hook");
    private boolean ignorePreStop = Boolean.parseBoolean(getSystemPropertyOrEnvVar("kubernetes.container.pre-stop-ignore"));

    private String project = getSystemPropertyOrEnvVar("docker.test.namespace", "default");
    private String imageName = getSystemPropertyOrEnvVar("docker.test.image", "cetestimage");

    private String username = getSystemPropertyOrEnvVar("docker.username", "");
    private String password = getSystemPropertyOrEnvVar("docker.password", "");
    private String email = getSystemPropertyOrEnvVar("docker.email", "");
    private String address = getSystemPropertyOrEnvVar("docker.address", "");

    private String webContext = getSystemPropertyOrEnvVar("arquillian.server.context", "");

    private long startupTimeout = Integer.parseInt(getSystemPropertyOrEnvVar("arquillian.startup.timeout", "60")); // 60sec

    private boolean ignoreCleanup = Boolean.parseBoolean(getSystemPropertyOrEnvVar("kubernetes.ignore.cleanup"));

    public void apply(Properties properties) {
        properties.put("namespace", getNamespace());
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

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getFromParent() {
        return fromParent;
    }

    public void setFromParent(String fromParent) {
        this.fromParent = fromParent;
    }

    public String getDeploymentDir() {
        return deploymentDir;
    }

    public void setDeploymentDir(String deploymentDir) {
        this.deploymentDir = deploymentDir;
    }

    public String getRegistryNamespace() {
        return registryNamespace;
    }

    public void setRegistryNamespace(String registryNamespace) {
        this.registryNamespace = registryNamespace;
    }

    public String getRegistryServiceName() {
        return registryServiceName;
    }

    public void setRegistryServiceName(String registryServiceName) {
        this.registryServiceName = registryServiceName;
    }

    public HookType getPreStopHookType() {
        return HookType.toHookType(preStopHookType);
    }

    public void setPreStopHookType(String preStopHookType) {
        this.preStopHookType = preStopHookType;
    }

    public String getPreStopPath() {
        return ("NONE".equals(preStopPath)) ? null : preStopPath;
    }

    public void setPreStopPath(String preStopPath) {
        this.preStopPath = preStopPath;
    }

    public boolean isIgnorePreStop() {
        return ignorePreStop;
    }

    public void setIgnorePreStop(boolean ignorePreStop) {
        this.ignorePreStop = ignorePreStop;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getWebContext() {
        return webContext;
    }

    public void setWebContext(String webContext) {
        this.webContext = webContext;
    }

    public long getStartupTimeout() {
        return startupTimeout;
    }

    public void setStartupTimeout(long startupTimeout) {
        this.startupTimeout = startupTimeout;
    }

    public boolean isIgnoreCleanup() {
        return ignoreCleanup;
    }

    public void setIgnoreCleanup(boolean ignoreCleanup) {
        this.ignoreCleanup = ignoreCleanup;
    }
}
