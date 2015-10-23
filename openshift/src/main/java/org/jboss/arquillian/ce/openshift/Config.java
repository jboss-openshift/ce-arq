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

package org.jboss.arquillian.ce.openshift;

import static org.jboss.arquillian.ce.utils.Strings.getSystemPropertyOrEnvVar;

import java.util.HashMap;
import java.util.Map;

public class Config {

    public static final String KUBERNETES_MASTER_SYSTEM_PROPERTY = "kubernetes.master";
    public static final String KUBERNETES_API_VERSION_SYSTEM_PROPERTY = "kubernetes.api.version";
    public static final String KUBERNETES_TLS_PROTOCOLS_SYSTEM_PROPERTY = "kubernetes.tls.protocols";
    public static final String KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY = "kubernetes.trust.certificates";
    public static final String KUBERNETES_CA_CERTIFICATE_FILE_SYSTEM_PROPERTY = "kubernetes.certs.ca.file";
    public static final String KUBERNETES_CA_CERTIFICATE_DATA_SYSTEM_PROPERTY = "kubernetes.certs.ca.data";
    public static final String KUBERNETES_CLIENT_CERTIFICATE_FILE_SYSTEM_PROPERTY = "kubernetes.certs.client.file";
    public static final String KUBERNETES_CLIENT_CERTIFICATE_DATA_SYSTEM_PROPERTY = "kubernetes.certs.client.data";
    public static final String KUBERNETES_CLIENT_KEY_FILE_SYSTEM_PROPERTY = "kubernetes.certs.client.key.file";
    public static final String KUBERNETES_CLIENT_KEY_DATA_SYSTEM_PROPERTY = "kubernetes.certs.client.key.data";
    public static final String KUBERNETES_CLIENT_KEY_ALGO_SYSTEM_PROPERTY = "kubernetes.certs.client.key.algo";
    public static final String KUBERNETES_CLIENT_KEY_PASSPHRASE_SYSTEM_PROPERTY = "kubernetes.certs.client.key.passphrase";
    public static final String KUBERNETES_AUTH_BASIC_USERNAME_SYSTEM_PROPERTY = "kubernetes.auth.basic.username";
    public static final String KUBERNETES_AUTH_BASIC_PASSWORD_SYSTEM_PROPERTY = "kubernetes.auth.basic.password";
    public static final String KUBERNETES_OAUTH_TOKEN_SYSTEM_PROPERTY = "kubernetes.auth.token";
    public static final String KUBERNETES_WATCH_RECONNECT_INTERVAL_SYSTEM_PROPERTY = "kubernetes.watch.reconnectInterval";
    public static final String KUBERNETES_WATCH_RECONNECT_LIMIT_SYSTEM_PROPERTY = "kubernetes.watch.reconnectLimit";
    public static final String KUBERNETES_REQUEST_TIMEOUT_SYSTEM_PROPERTY = "kubernetes.request.timeout";
    public static final String KUBERNETES_ROLLING_TIMEOUT_SYSTEM_PROPERTY = "kubernetes.rolling.timeout";
    public static final String KUBERNETES_LOGGING_INTERVAL_SYSTEM_PROPERTY = "kubernetes.logging.interval";

    public static final String KUBERNETES_NAMESPACE_SYSTEM_PROPERTY = "kubernetes.namespace";
    public static final String KUBERNETES_HTTP_PROXY = "http.proxy";
    public static final String KUBERNETES_HTTPS_PROXY = "https.proxy";
    public static final String KUBERNETES_ALL_PROXY = "all.proxy";

    public static final Long DEFAULT_ROLLING_TIMEOUT = 15 * 60 * 1000L;
    public static final int DEFAULT_LOGGING_INTERVAL = 20 * 1000;

    private boolean trustCerts;

    private String masterUrl = "https://kubernetes.default.svc";
    private String apiVersion = "v1";
    private String namespace;
    private String[] enabledProtocols = new String[]{"TLSv1.2"};
    private String caCertFile;
    private String caCertData;
    private String clientCertFile;
    private String clientCertData;
    private String clientKeyFile;
    private String clientKeyData;
    private String clientKeyAlgo = "RSA";
    private String clientKeyPassphrase = "changeit";
    private String username;
    private String password;
    private String oauthToken;
    private int watchReconnectInterval = 1000;
    private int watchReconnectLimit = -1;
    private int requestTimeout = 10 * 1000;
    private long rollingTimeout = DEFAULT_ROLLING_TIMEOUT;
    private int loggingInterval = DEFAULT_LOGGING_INTERVAL;
    private String proxy;

    private Map<Integer, String> errorMessages = new HashMap<>();

    public Config(String masterUrl) {
        setMasterUrl(masterUrl);

        configFromSysPropsOrEnvVars(this);

        if (!this.masterUrl.endsWith("/")) {
            this.masterUrl = this.masterUrl + "/";
        }
    }

    public void configFromSysPropsOrEnvVars(Config config) {
        String trustCerts = getSystemPropertyOrEnvVar(KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, String.valueOf(config.isTrustCerts()));
        config.setTrustCerts(Boolean.valueOf(trustCerts));
        config.setMasterUrl(getSystemPropertyOrEnvVar(KUBERNETES_MASTER_SYSTEM_PROPERTY, config.getMasterUrl()));
        config.setApiVersion(getSystemPropertyOrEnvVar(KUBERNETES_API_VERSION_SYSTEM_PROPERTY, config.getApiVersion()));
        config.setNamespace(getSystemPropertyOrEnvVar(KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, config.getNamespace()));
        config.setCaCertFile(getSystemPropertyOrEnvVar(KUBERNETES_CA_CERTIFICATE_FILE_SYSTEM_PROPERTY, config.getCaCertFile()));
        config.setCaCertData(getSystemPropertyOrEnvVar(KUBERNETES_CA_CERTIFICATE_DATA_SYSTEM_PROPERTY, config.getCaCertData()));
        config.setClientCertFile(getSystemPropertyOrEnvVar(KUBERNETES_CLIENT_CERTIFICATE_FILE_SYSTEM_PROPERTY, config.getClientCertFile()));
        config.setClientCertData(getSystemPropertyOrEnvVar(KUBERNETES_CLIENT_CERTIFICATE_DATA_SYSTEM_PROPERTY, config.getClientCertData()));
        config.setClientKeyFile(getSystemPropertyOrEnvVar(KUBERNETES_CLIENT_KEY_FILE_SYSTEM_PROPERTY, config.getClientKeyFile()));
        config.setClientKeyData(getSystemPropertyOrEnvVar(KUBERNETES_CLIENT_KEY_DATA_SYSTEM_PROPERTY, config.getClientKeyData()));
        config.setClientKeyAlgo(getSystemPropertyOrEnvVar(KUBERNETES_CLIENT_KEY_ALGO_SYSTEM_PROPERTY, config.getClientKeyAlgo()));
        config.setClientKeyPassphrase(getSystemPropertyOrEnvVar(KUBERNETES_CLIENT_KEY_PASSPHRASE_SYSTEM_PROPERTY, config.getClientKeyPassphrase()));

        config.setOauthToken(getSystemPropertyOrEnvVar(KUBERNETES_OAUTH_TOKEN_SYSTEM_PROPERTY, config.getOauthToken()));
        config.setUsername(getSystemPropertyOrEnvVar(KUBERNETES_AUTH_BASIC_USERNAME_SYSTEM_PROPERTY, config.getUsername()));
        config.setPassword(getSystemPropertyOrEnvVar(KUBERNETES_AUTH_BASIC_PASSWORD_SYSTEM_PROPERTY, config.getPassword()));
        String configuredProtocols = getSystemPropertyOrEnvVar(KUBERNETES_TLS_PROTOCOLS_SYSTEM_PROPERTY);
        if (configuredProtocols != null) {
            config.setEnabledProtocols(configuredProtocols.split(","));
        }
        String configuredWatchReconnectInterval = getSystemPropertyOrEnvVar(KUBERNETES_WATCH_RECONNECT_INTERVAL_SYSTEM_PROPERTY);
        if (configuredWatchReconnectInterval != null) {
            config.setWatchReconnectInterval(Integer.parseInt(configuredWatchReconnectInterval));
        }

        String configuredWatchReconnectLimit = getSystemPropertyOrEnvVar(KUBERNETES_WATCH_RECONNECT_LIMIT_SYSTEM_PROPERTY);
        if (configuredWatchReconnectLimit != null) {
            config.setWatchReconnectLimit(Integer.parseInt(configuredWatchReconnectLimit));
        }

        String configuredRollingTimeout = getSystemPropertyOrEnvVar(KUBERNETES_ROLLING_TIMEOUT_SYSTEM_PROPERTY, String.valueOf(DEFAULT_ROLLING_TIMEOUT));
        if (configuredRollingTimeout != null) {
            config.setRollingTimeout(Long.parseLong(configuredRollingTimeout));
        }


        String configuredLoggingInterval = getSystemPropertyOrEnvVar(KUBERNETES_LOGGING_INTERVAL_SYSTEM_PROPERTY, String.valueOf(DEFAULT_LOGGING_INTERVAL));
        if (configuredLoggingInterval != null) {
            config.setLoggingInterval(Integer.parseInt(configuredLoggingInterval));
        }

        String requestTimeout = getSystemPropertyOrEnvVar(KUBERNETES_REQUEST_TIMEOUT_SYSTEM_PROPERTY, String.valueOf(config.getRequestTimeout()));
        config.setRequestTimeout(Integer.parseInt(requestTimeout));

        config.setProxy(getSystemPropertyOrEnvVar(KUBERNETES_ALL_PROXY, config.getProxy()));
        config.setProxy(getSystemPropertyOrEnvVar(KUBERNETES_HTTPS_PROXY, config.getProxy()));
        config.setProxy(getSystemPropertyOrEnvVar(KUBERNETES_HTTP_PROXY, config.getProxy()));
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getClientKeyPassphrase() {
        return clientKeyPassphrase;
    }

    public void setClientKeyPassphrase(String clientKeyPassphrase) {
        this.clientKeyPassphrase = clientKeyPassphrase;
    }

    public String getClientKeyAlgo() {
        return clientKeyAlgo;
    }

    public void setClientKeyAlgo(String clientKeyAlgo) {
        this.clientKeyAlgo = clientKeyAlgo;
    }

    public String getClientKeyData() {
        return clientKeyData;
    }

    public void setClientKeyData(String clientKeyData) {
        this.clientKeyData = clientKeyData;
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    public String getClientCertData() {
        return clientCertData;
    }

    public void setClientCertData(String clientCertData) {
        this.clientCertData = clientCertData;
    }

    public String getClientCertFile() {
        return clientCertFile;
    }

    public void setClientCertFile(String clientCertFile) {
        this.clientCertFile = clientCertFile;
    }

    public String getCaCertData() {
        return caCertData;
    }

    public void setCaCertData(String caCertData) {
        this.caCertData = caCertData;
    }

    public String getCaCertFile() {
        return caCertFile;
    }

    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public boolean isTrustCerts() {
        return trustCerts;
    }

    public void setTrustCerts(boolean trustCerts) {
        this.trustCerts = trustCerts;
    }

    public int getWatchReconnectInterval() {
        return watchReconnectInterval;
    }

    public void setWatchReconnectInterval(int watchReconnectInterval) {
        this.watchReconnectInterval = watchReconnectInterval;
    }

    public int getWatchReconnectLimit() {
        return watchReconnectLimit;
    }

    public void setWatchReconnectLimit(int watchReconnectLimit) {
        this.watchReconnectLimit = watchReconnectLimit;
    }

    public Map<Integer, String> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(Map<Integer, String> errorMessages) {
        this.errorMessages = errorMessages;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getRollingTimeout() {
        return rollingTimeout;
    }

    public void setRollingTimeout(long rollingTimeout) {
        this.rollingTimeout = rollingTimeout;
    }

    public int getLoggingInterval() {
        return loggingInterval;
    }

    public void setLoggingInterval(int loggingInterval) {
        this.loggingInterval = loggingInterval;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getProxy() {
        return proxy;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
