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

package org.jboss.arquillian.ce.eap6;

import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Strings;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CEConfiguration extends Configuration {
    private String eapImageName = Strings.getSystemPropertyOrEnvVar("container.image.name", "ce-registry.usersys.redhat.com/jboss-eap-6/eap64-openshift:1.2");
    private String label = Strings.getSystemPropertyOrEnvVar("container.image.label", "eap6bare");
    private int mgmtPort = Integer.parseInt(Strings.getSystemPropertyOrEnvVar("container.mgmt.port", "9990"));

    public String getEapImageName() {
        return eapImageName;
    }

    public void setEapImageName(String eapImageName) {
        this.eapImageName = eapImageName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getMgmtPort() {
        return mgmtPort;
    }

    public void setMgmtPort(int mgmtPort) {
        this.mgmtPort = mgmtPort;
    }
}
