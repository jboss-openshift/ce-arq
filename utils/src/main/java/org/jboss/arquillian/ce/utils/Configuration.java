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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.jboss.arquillian.ce.api.ConfigurationHandle;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class Configuration implements ContainerConfiguration, ConfigurationHandle, Serializable {
    private static final long serialVersionUID = 1L;

    private String kubernetesMaster = getSystemPropertyOrEnvVar("kubernetes.master");
    private String dockerUrl = getSystemPropertyOrEnvVar("docker.url");

    private String openshiftUsername = getSystemPropertyOrEnvVar("openshift.username", "admin");
    private String openshiftPassword = getSystemPropertyOrEnvVar("openshift.password", "admin");

    private String apiVersion = getSystemPropertyOrEnvVar("kubernetes.api.version", "v1");
    private String namespacePrefix = getSystemPropertyOrEnvVar("kubernetes.namespace.prefix", "cearq");
    private String namespace = getSystemPropertyOrEnvVar("kubernetes.namespace");
    private String token = getSystemPropertyOrEnvVar("kubernetes.auth.token");
    private boolean trustCerts = Boolean.valueOf(getSystemPropertyOrEnvVar("kubernetes.trust.certs", "true"));
    private boolean generatedNS;

    private String fromParent = getSystemPropertyOrEnvVar("from.parent");
    private String deploymentDir = getSystemPropertyOrEnvVar("deployment.dir");

    private String registryType = getSystemPropertyOrEnvVar("kubernetes.registry.type", "static");
    private String registryURL = getSystemPropertyOrEnvVar("kubernetes.registry.url", "ce-os-registry.usersys.redhat.com:5000"); // add port directly on purpose
    private String registryPort = getSystemPropertyOrEnvVar("kubernetes.registry.port");
    private String registryNamespace = getSystemPropertyOrEnvVar("kubernetes.registry.namespace", "default");
    private String registryServiceName = getSystemPropertyOrEnvVar("kubernetes.registry.service.name", "docker-registry");

    private String preStopHookType = getSystemPropertyOrEnvVar("kubernetes.container.pre-stop-hook-type", HookType.HTTP_GET.name());
    private String preStopPath = getSystemPropertyOrEnvVar("kubernetes.container.pre-stop", "/pre-stop/_hook");
    private boolean ignorePreStop = Boolean.parseBoolean(getSystemPropertyOrEnvVar("kubernetes.container.pre-stop-ignore"));

    private String probeHookType = getSystemPropertyOrEnvVar("kubernetes.readiness.probe.hook-type", HookType.EXEC.name());
    private String probeCommands = getSystemPropertyOrEnvVar("kubernetes.readiness.probe.commands");

    private String templateName = getSystemPropertyOrEnvVar("docker.file.template", "Dockerfile_template");

    private String imageGroup = getSystemPropertyOrEnvVar("docker.test.image", "cetestimage");
    private String imageTag = getSystemPropertyOrEnvVar("docker.test.tag", "latest");
    private String imagePullPolicy = getSystemPropertyOrEnvVar("docker.test.pull.policy", "Always");

    private String dockerUsername = getSystemPropertyOrEnvVar("docker.username", "");
    private String dockerPassword = getSystemPropertyOrEnvVar("docker.password", "");
    private String dockerEmail = getSystemPropertyOrEnvVar("docker.email", "");
    private String dockerAddress = getSystemPropertyOrEnvVar("docker.address", "");

    private long startupTimeout = Integer.parseInt(getSystemPropertyOrEnvVar("arquillian.startup.timeout", "600")); // 10min ...

    private boolean ignoreCleanup = Boolean.parseBoolean(getSystemPropertyOrEnvVar("kubernetes.ignore.cleanup"));

    protected String generateNS() {
        StringBuilder builder = new StringBuilder();
        if (getNamespacePrefix() != null) {
            builder.append(getNamespacePrefix()).append("-");
        }
        Random random = new Random();
        int N = 8;
        while (N > 0) {
            int i = Math.abs(random.nextInt('z' - 'a' + 1));
            char ch = (char) ('a' + i);
            builder.append(ch);
            N--;
        }
        builder.append(Math.abs(random.nextInt(1000)));
        return builder.toString();
    }

    public Properties getProperties() {
        Properties properties = new Properties();
        apply(properties);
        return properties;
    }

    protected void apply(Properties properties) {
        // namespace
        properties.put("kubernetes.namespace", getNamespace());
        properties.put("namespace", getNamespace());
        // api version
        properties.put("version", getApiVersion());
        properties.put("kubernetes.api.version", getApiVersion());
    }

    public void validate() throws ConfigurationException {
        if (kubernetesMaster == null) {
            throw new ConfigurationException("Null Kubernetes master!");
        }

        if ((isNullOrEmpty(openshiftUsername) || isNullOrEmpty(openshiftPassword)) && isNullOrEmpty(token)) {
            throw new ConfigurationException("Missing OpenShift authentification -- username/password or token!");
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

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public String getNamespace() {
        if (namespace == null) {
            namespace = generateNS();
            generatedNS = true;
        }
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isGeneratedNS() {
        return generatedNS;
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

    public String getRegistryType() {
        return registryType;
    }

    public void setRegistryType(String registryType) {
        this.registryType = registryType;
    }

    public String getRegistryURL() {
        return registryURL;
    }

    public void setRegistryURL(String registryURL) {
        this.registryURL = registryURL;
    }

    public String getRegistryPort() {
        return registryPort;
    }

    public void setRegistryPort(String registryPort) {
        this.registryPort = registryPort;
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
        return preStopPath;
    }

    public void setPreStopPath(String preStopPath) {
        this.preStopPath = preStopPath;
    }

    public HookType getProbeHookType() {
        return HookType.toHookType(probeHookType);
    }

    public void setProbeHookType(String probeHookType) {
        this.probeHookType = probeHookType;
    }

    public List<String> getProbeCommands() {
        if (probeCommands == null) {
            return null;
        } else {
            return Arrays.asList(probeCommands.split(","));
        }
    }

    public void setProbeCommands(String probeCommands) {
        this.probeCommands = probeCommands;
    }

    public boolean isIgnorePreStop() {
        return ignorePreStop;
    }

    public void setIgnorePreStop(boolean ignorePreStop) {
        this.ignorePreStop = ignorePreStop;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getImageGroup() {
        return imageGroup;
    }

    public void setImageGroup(String imageGroup) {
        this.imageGroup = imageGroup;
    }

    public String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public String getDockerUsername() {
        return dockerUsername;
    }

    public void setDockerUsername(String dockerUsername) {
        this.dockerUsername = dockerUsername;
    }

    public String getDockerPassword() {
        return dockerPassword;
    }

    public void setDockerPassword(String dockerPassword) {
        this.dockerPassword = dockerPassword;
    }

    public String getDockerEmail() {
        return dockerEmail;
    }

    public void setDockerEmail(String dockerEmail) {
        this.dockerEmail = dockerEmail;
    }

    public String getDockerAddress() {
        return dockerAddress;
    }

    public void setDockerAddress(String dockerAddress) {
        this.dockerAddress = dockerAddress;
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

    public boolean performCleanup() {
        return (isIgnoreCleanup() == false); // dup negative ;-)
    }
}
