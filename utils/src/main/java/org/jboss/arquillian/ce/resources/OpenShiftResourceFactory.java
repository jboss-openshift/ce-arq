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
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.api.OpenShiftResource;
import org.jboss.arquillian.ce.api.OpenShiftResources;
import org.jboss.arquillian.ce.api.RoleBinding;
import org.jboss.arquillian.ce.api.RoleBindings;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OpenShiftResourceFactory {
    private static final Logger log = Logger.getLogger(OpenShiftResourceFactory.class.getName());

    private static final OSRFinder OSR_FINDER = new OSRFinder();
    private static final RBFinder RB_FINDER = new RBFinder();

    public static void createResources(String resourcesKey, OpenShiftAdapter adapter, Archive<?> archive, Class<?> testClass) {
        try {
            final ValueExpressionResolver resolver = adapter.createValueExpressionResolver(new Properties());

            List<OpenShiftResource> openShiftResources = new ArrayList<>();
            OSR_FINDER.findAnnotations(openShiftResources, testClass);
            for (OpenShiftResource osr : openShiftResources) {
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

                log.info(String.format("Creating new OpenShift resource: %s", file));
                adapter.createResource(resourcesKey, stream);
            }
            
            List<RoleBinding> roleBindings = new ArrayList<>();
            RB_FINDER.findAnnotations(roleBindings, testClass);
            for (RoleBinding rb : roleBindings) {
                String roleRefName = new ValueExpression(rb.roleRefName()).resolveString(resolver);
                String userName = new ValueExpression(rb.userName()).resolveString(resolver);
                log.info(String.format("Adding new role binding: %s / %s", roleRefName, userName));
                adapter.addRoleBinding(resourcesKey, roleRefName, userName);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static abstract class Finder<U extends Annotation, V extends Annotation> {

        protected abstract Class<U> getWrapperType();

        protected abstract Class<V> getSingleType();

        protected abstract V[] toSingles(U u);

        void findAnnotations(List<V> annotations, Class<?> testClass) {
            if (testClass == Object.class) {
                return;
            }

            U anns = testClass.getAnnotation(getWrapperType());
            if (anns != null) {
                V[] ann = toSingles(anns);
                for (int i = ann.length - 1; i >= 0; i--) {
                    annotations.add(0, ann[i]);
                }
            }

            V ann = testClass.getAnnotation(getSingleType());
            if (ann != null) {
                annotations.add(0, ann);
            }

            findAnnotations(annotations, testClass.getSuperclass());
        }

    }

    private static class OSRFinder extends Finder<OpenShiftResources, OpenShiftResource> {
        protected Class<OpenShiftResources> getWrapperType() {
            return OpenShiftResources.class;
        }

        protected Class<OpenShiftResource> getSingleType() {
            return OpenShiftResource.class;
        }

        protected OpenShiftResource[] toSingles(OpenShiftResources openShiftResources) {
            return openShiftResources.value();
        }
    }

    private static class RBFinder extends Finder<RoleBindings, RoleBinding> {
        protected Class<RoleBindings> getWrapperType() {
            return RoleBindings.class;
        }

        protected Class<RoleBinding> getSingleType() {
            return RoleBinding.class;
        }

        protected RoleBinding[] toSingles(RoleBindings roleBindings) {
            return roleBindings.value();
        }
    }
}
