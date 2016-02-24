/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other
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
package org.jboss.arquillian.ce.cube;

import java.util.Map;

import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.container.spi.ConfigurationException;

/**
 * CECubeConfiguration
 * <p/>
 * Configuration for Cloud Enablement Arquillian Cube extension.
 * 
 * @author Rob Cernich
 */
public class CECubeConfiguration extends Configuration {
    private static final long serialVersionUID = 1L;

    private String templateURL = Strings.getSystemPropertyOrEnvVar("openshift.template.url");
    private String templateLabels = Strings.getSystemPropertyOrEnvVar("openshift.template.labels");
    private String templateParameters = Strings.getSystemPropertyOrEnvVar("openshift.template.parameters");
    private boolean templateProcess = Boolean.valueOf(Strings.getSystemPropertyOrEnvVar("openshift.template.process", "true"));

    public static CECubeConfiguration fromMap(final Map<String, String> props) {
        //XXX: need to rename arq.ce-cube properties.  arq extension properties cannot contain '.'
        final CECubeConfiguration config = new CECubeConfiguration();
        config.setApiVersion(getProperty(props, "kubernetesApiVersion", config.getApiVersion()));
        config.setIgnoreCleanup(Boolean.valueOf(getProperty(props, "kubernetesIgnoreCleanup", Boolean.toString(config.isIgnoreCleanup()))));
        config.setKubernetesMaster(getProperty(props, "kubernetesMaster", config.getKubernetesMaster()));
        config.setNamespace(getProperty(props, "kubernetesNamespace", null));
        config.setNamespacePrefix(getProperty(props, "kubernetesNamespacePrefix", config.getNamespacePrefix()));
        config.setOpenshiftPassword(getProperty(props, "openshiftPassword", config.getOpenshiftPassword()));
        config.setOpenshiftUsername(getProperty(props, "openshiftUsername", config.getOpenshiftUsername()));
        config.setStartupTimeout(Long.valueOf(getProperty(props, "arquillianStartupTimeout", Long.toString(config.getStartupTimeout()))));
        config.setTemplateLabels(getProperty(props, "openshiftTemplateLabels", config.templateLabels));
        config.setTemplateParameters(getProperty(props, "openshiftTemplateParameters", config.templateParameters));
        config.setTemplateProcess(Boolean.valueOf(getProperty(props, "openshiftTemplateProcess", Boolean.toString(config.templateProcess))));
        config.setTemplateURL(getProperty(props, "openshiftTemplateUrl", config.templateURL));
        config.setToken(getProperty(props, "kubernetesAuthToken", config.getToken()));
        config.setTrustCerts(Boolean.valueOf(getProperty(props, "kubernetesTrustCerts", Boolean.toString(config.isTrustCerts()))));
        config.loadApplications(props);
        config.validate();
        return config;
    }
    
    private void loadApplications(Map<String, String> props) {
        //TODO: parse application properties, e.g. templates, parms, env, role bindings, etc.
        // this might replace @Template
    }

    public String getTemplateURL() {
        return templateURL;
    }

    public void setTemplateURL(String templateURL) {
        this.templateURL = templateURL;
    }

    public Map<String, String> getTemplateLabels() {
        return Strings.splitKeyValueList(templateLabels);
    }

    public void setTemplateLabels(String templateLabels) {
        this.templateLabels = templateLabels;
    }

    public Map<String, String> getTemplateParameters() {
        return Strings.splitKeyValueList(templateParameters);
    }

    public void setTemplateParameters(String templateParameters) {
        this.templateParameters = templateParameters;
    }

    public boolean isTemplateProcess() {
        return templateProcess;
    }

    public void setTemplateProcess(boolean templateProcess) {
        this.templateProcess = templateProcess;
    }

    private static String getProperty(final Map<String, String> props, final String key, final String defaultValue) {
        final String value = props.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
