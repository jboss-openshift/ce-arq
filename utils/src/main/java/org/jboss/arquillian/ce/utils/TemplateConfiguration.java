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

import java.util.Map;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class TemplateConfiguration extends Configuration implements TemplateAwareConfiguration {
    private static final long serialVersionUID = 1L;

    private String templateURL = Strings.getSystemPropertyOrEnvVar("openshift.template.url");
    private String templateLabels = Strings.getSystemPropertyOrEnvVar("openshift.template.labels");
    private String templateParameters = Strings.getSystemPropertyOrEnvVar("openshift.template.parameters");
    private boolean templateProcess = Boolean.valueOf(Strings.getSystemPropertyOrEnvVar("openshift.template.process", "true"));

    public String getTemplateURL() {
        return templateURL;
    }

    public void setTemplateURL(String templateURL) {
        this.templateURL = templateURL;
    }

    protected String getTemplateLabelsRaw() {
        return templateLabels;
    }

    public Map<String, String> getTemplateLabels() {
        return Strings.splitKeyValueList(templateLabels);
    }

    public void setTemplateLabels(String templateLabels) {
        this.templateLabels = templateLabels;
    }

    public String getTemplateParametersRaw() {
        return templateParameters;
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
}
