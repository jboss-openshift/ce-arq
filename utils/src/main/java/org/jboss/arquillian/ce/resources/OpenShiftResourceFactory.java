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

package org.jboss.arquillian.ce.resources;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.api.OpenShiftResource;
import org.jboss.arquillian.ce.api.OpenShiftResources;
import org.jboss.arquillian.ce.utils.CustomValueExpressionResolver;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OpenShiftResourceFactory {
    private static void findAnnotations(List<OpenShiftResource> annotations, Class<?> testClass) {
        if (testClass == Object.class) {
            return;
        }

        OpenShiftResources anns = testClass.getAnnotation(OpenShiftResources.class);
        if (anns != null) {
            OpenShiftResource[] osrs = anns.value();
            for (int i = osrs.length - 1; i >= 0; i--) {
                annotations.add(0, osrs[i]);
            }
            Collections.addAll(annotations, anns.value());
        }

        OpenShiftResource ann = testClass.getAnnotation(OpenShiftResource.class);
        if (ann != null) {
            annotations.add(0, ann);
        }

        findAnnotations(annotations, testClass.getSuperclass());
    }

    public static void createResources(String resourcesKey, OpenShiftAdapter adapter, Archive<?> archive, Class<?> testClass) {
        try {
            List<OpenShiftResource> annotations = new ArrayList<>();
            findAnnotations(annotations, testClass);
            if (annotations.isEmpty()) {
                return;
            }

            ValueExpressionResolver resolver = new CustomValueExpressionResolver();
            for (OpenShiftResource osr : annotations) {
                ValueExpression ve = new ValueExpression(osr.value());
                String file = ve.resolveString(resolver);

                InputStream stream;
                if (file.startsWith("http")) {
                    stream = new URL(file).openStream();
                } else if (file.startsWith("classpath:")) {
                    stream = testClass.getClassLoader().getResourceAsStream(file);
                } else if (file.startsWith("archive:")) {
                    stream = archive.get(file).getAsset().openStream();
                } else {
                    stream = new ByteArrayInputStream(file.getBytes());
                }

                adapter.createResource(resourcesKey, stream);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
