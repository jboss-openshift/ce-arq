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

import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.ce.utils.TemplateConfiguration;

/**
 * CECubeConfiguration
 * <p/>
 * Configuration for Cloud Enablement Arquillian Cube extension.
 * 
 * @author Rob Cernich
 */
public class CECubeConfiguration extends TemplateConfiguration {
    private static final long serialVersionUID = 1L;

    private OpenShiftClient client;

    private int routerSniPort = Integer.valueOf(Strings.getSystemPropertyOrEnvVar("openshift.router.sniPort", "443"));

    public static CECubeConfiguration fromMap(final Map<String, String> props) {
        //XXX: need to rename arq.ce-cube properties.  arq extension properties cannot contain '.'
        final CECubeConfiguration config = new CECubeConfiguration();
        config.setOpenshiftPassword(getProperty(props, "openshiftPassword", config.getOpenshiftPassword()));
        config.setOpenshiftUsername(getProperty(props, "openshiftUsername", config.getOpenshiftUsername()));
        config.setRouterSniPort(Integer.valueOf(getProperty(props, "routerSniPort", Integer.toString(config.routerSniPort))));
        config.setStartupTimeout(Long.valueOf(getProperty(props, "arquillianStartupTimeout", Long.toString(config.getStartupTimeout()))));
        config.setTemplateLabels(getProperty(props, "openshiftTemplateLabels", config.getTemplateLabelsRaw()));
        config.setTemplateParameters(getProperty(props, "openshiftTemplateParameters", config.getTemplateParametersRaw()));
        config.setTemplateProcess(Boolean.valueOf(getProperty(props, "openshiftTemplateProcess", Boolean.toString(config.isTemplateProcess()))));
        config.setTemplateURL(getProperty(props, "openshiftTemplateUrl", config.getTemplateURL()));
        config.setToken(getProperty(props, "kubernetesAuthToken", config.getToken()));
        config.setTrustCerts(Boolean.valueOf(getProperty(props, "kubernetesTrustCerts", Boolean.toString(config.isTrustCerts()))));
        config.loadApplications(props);
        return config;
    }
    
    private void loadApplications(Map<String, String> props) {
        //TODO: parse application properties, e.g. templates, parms, env, role bindings, etc.
        // this might replace @Template
    }

    public int getRouterSniPort() {
        return routerSniPort;
    }

    public void setRouterSniPort(int routerSniPort) {
        this.routerSniPort = routerSniPort;
    }

    private static String getProperty(final Map<String, String> props, final String key, final String defaultValue) {
        final String value = props.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public void setClient(OpenShiftClient client) {
        this.client = client;
    }

    @Override
    public String getToken() {
        String token = super.getToken();

        if ((token == null || token.isEmpty()) && (client != null)) {
            token = client.getClientExt().getConfiguration().getOauthToken();
        }

        return token;
    }
}