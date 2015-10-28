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

package org.jboss.arquillian.ce.template;

import java.io.Serializable;

import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Strings;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TemplateCEConfiguration extends Configuration implements Serializable {
    private static final long serialVersionUID = 1L;

    private String templateURL = Strings.getSystemPropertyOrEnvVar("openshift.template.url");

    private String gitRepository = Strings.getSystemPropertyOrEnvVar("git.repository");
    private String gitCredentials = Strings.getSystemPropertyOrEnvVar("git.credentials");
    private String gitUsername = Strings.getSystemPropertyOrEnvVar("git.username");
    private String gitPassword = Strings.getSystemPropertyOrEnvVar("git.password");

    public boolean isNetRC() {
        return "netrc".equalsIgnoreCase(getGitCredentials());
    }

    public String getTemplateURL() {
        return templateURL;
    }

    public void setTemplateURL(String templateURL) {
        this.templateURL = templateURL;
    }

    public String getGitRepository(boolean fail) {
        if (gitRepository == null && fail) {
            throw new IllegalArgumentException("Missing git repository!");
        }
        return gitRepository;
    }

    public void setGitRepository(String gitRepository) {
        this.gitRepository = gitRepository;
    }

    public String getGitCredentials() {
        return gitCredentials;
    }

    public void setGitCredentials(String gitCredentials) {
        this.gitCredentials = gitCredentials;
    }

    public String getGitUsername() {
        if (gitUsername == null) {
            throw new IllegalArgumentException("Missing git username!");
        }
        return gitUsername;
    }

    public void setGitUsername(String gitUsername) {
        this.gitUsername = gitUsername;
    }

    public String getGitPassword() {
        if (gitPassword == null) {
            throw new IllegalArgumentException("Missing git password!");
        }
        return gitPassword;
    }

    public void setGitPassword(String gitPassword) {
        this.gitPassword = gitPassword;
    }
}
