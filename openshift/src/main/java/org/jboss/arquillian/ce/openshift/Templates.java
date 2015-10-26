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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
final class Templates {

    static final String PROJECT_REQUEST = "project_request";
    static final String REPLICATION_CONTROLLER = "replication_controller";
    static final String LIFECYCLE = "lifecycle";

    static String readJson(String apiVersion, String json) {
        return readJson(apiVersion, json, null);
    }

    static String readJson(String apiVersion, String json, String env) {
        try {
            StringBuilder builder = new StringBuilder();
            String path = String.format("%s_%s%s.json", apiVersion, json, env != null && env.length() > 0 ? "_" + env : "");
            InputStream stream = Templates.class.getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                throw new IllegalArgumentException("No such template: " + path);
            }
            try {
                int ch;
                while ((ch = stream.read()) != -1) {
                    builder.append((char) ch);
                }
            } finally {
                stream.close();
            }
            return builder.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
