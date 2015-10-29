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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Strings {
    private static final int INDEX_NOT_FOUND = -1;

    private static String checkForNone(String value) {
        return "__none".equalsIgnoreCase(value) ? null : value;
    }

    private static String getSystemPropertyOrEnvVar(String systemPropertyName, String envVarName, String defaultValue) {
        String answer = System.getProperty(systemPropertyName);
        if (answer != null) {
            return checkForNone(answer);
        }

        answer = System.getenv(envVarName);
        if (answer != null) {
            return checkForNone(answer);
        }

        return checkForNone(defaultValue);
    }

    private static String convertSystemPropertyNameToEnvVar(String systemPropertyName) {
        return systemPropertyName.toUpperCase().replaceAll("[.-]", "_");
    }

    // ---

    public static int parseNumber(String value) {
        int k = 1;
        int n = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch) == false) {
                break;
            }
            n += (ch - '0') * k;
            k *= 10;
        }
        return n;
    }

    public static String toValue(String value, String defaultValue) {
        return (value != null) ? value : defaultValue;
    }

    public static String getSystemPropertyOrEnvVar(String key) {
        return getSystemPropertyOrEnvVar(key, null);
    }

    public static String getSystemPropertyOrEnvVar(String key, String defaultValue) {
        return getSystemPropertyOrEnvVar(key, convertSystemPropertyNameToEnvVar(key), defaultValue);
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotNullOrEmpty(String str) {
        return isNullOrEmpty(str) == false;
    }

    public static Map<String, String> splitKeyValueList(String string) {
        Map<String, String> labels = new HashMap<>();
        if (string != null && string.length() > 0) {
            String[] split = string.split(",");
            for (String s : split) {
                String[] ss = s.split("=");
                labels.put(ss[0], ss[1]);
            }
        }
        return labels;
    }

    // ---

    static String toString(InputStream stream) {
        try {
            StringWriter writer = new StringWriter();
            LineIterator itr = IOUtils.lineIterator(stream, "UTF-8");
            while (itr.hasNext()) {
                String line = itr.next();
                writer.write(line);
                if (itr.hasNext()) writer.write("\n");
            }
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    static String substringBetween(String str, String open, String close) {
        if (str == null || open == null || close == null) {
            return null;
        }
        int start = str.indexOf(open);
        if (start != INDEX_NOT_FOUND) {
            int end = str.indexOf(close, start + open.length());
            if (end != INDEX_NOT_FOUND) {
                return str.substring(start + open.length(), end);
            }
        }
        return null;
    }
}
