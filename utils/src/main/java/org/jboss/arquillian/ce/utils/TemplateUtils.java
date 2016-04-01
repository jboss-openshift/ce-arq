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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.ce.api.Replicas;
import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.ce.api.TemplateParameter;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.test.spi.TestClass;

/**
 * Template utils.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TemplateUtils {

    @SuppressWarnings({"rawtypes", "unchecked" })
    public static void addParameterValues(List<ParamValue> values, Map map, boolean filter) {
        Set<Map.Entry> entries = map.entrySet();
        for (Map.Entry env : entries) {
            if (env.getKey() instanceof String && env.getValue() instanceof String) {
                String key = (String) env.getKey();
                if (filter == false || key.startsWith("ARQ_") || key.startsWith("arq_")) {
                    if (filter) {
                        values.add(new ParamValue(key.substring("ARQ_".length()), (String) env.getValue()));
                    } else {
                        values.add(new ParamValue(key, (String) env.getValue()));
                    }
                }
            }
        }
    }

    public static String readTemplateUrl(Template template, TemplateAwareConfiguration configuration, StringResolver resolver) {
        String templateUrl = template == null ? null : template.url();

        if (null != configuration.getTemplateURL()) {
            templateUrl = configuration.getTemplateURL();
        }

        return templateUrl;
    }

    public static int readReplicas(TestClass testClass) {
        Replicas replicas = testClass.getAnnotation(Replicas.class);
        int r = -1;
        if (replicas != null) {
            if (replicas.value() <= 0) {
                throw new IllegalArgumentException("Non-positive replicas size: " + replicas.value());
            }
            r = replicas.value();
        }
        int max = 0;
        for (Method c : testClass.getMethods(TargetsContainer.class)) {
            int index = Strings.parseNumber(c.getAnnotation(TargetsContainer.class).value());
            if (r > 0 && index >= r) {
                throw new IllegalArgumentException(String.format("Node / pod index bigger then replicas; %s >= %s ! (%s)", index, r, c));
            }
            max = Math.max(max, index);
        }
        if (r < 0) {
            return max + 1;
        } else {
            return r;
        }
    }

    public static Map<String, String> readLabels(Template template, TemplateAwareConfiguration configuration, StringResolver resolver) {
        if (template != null) {
            String string = template.labels();
            if (string != null && string.length() > 0) {
                Map<String, String> map = Strings.splitKeyValueList(string);
                Map<String, String> resolved = new HashMap<>();
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    resolved.put(resolver.resolve(entry.getKey()), resolver.resolve(entry.getValue()));
                }
                return resolved;
            }
        }
        return configuration.getTemplateLabels();
    }

    public static boolean executeProcessTemplate(Template template, TemplateAwareConfiguration configuration) {
        return (template == null || template.process()) && configuration.isTemplateProcess();
    }

    public static Map<String, String> readParameters(Template template, TemplateAwareConfiguration configuration, StringResolver resolver) {
        if (template != null) {
            TemplateParameter[] parameters = template.parameters();
            Map<String, String> map = new HashMap<>();
            for (TemplateParameter parameter : parameters) {
                String name = resolver.resolve(parameter.name());
                String value = resolver.resolve(parameter.value());
                map.put(name, value);
            }
            return map;
        }
        return configuration.getTemplateParameters();
    }
}
