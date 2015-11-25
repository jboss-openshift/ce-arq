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
import org.jboss.arquillian.ce.api.AddRoleToServiceAccount;
import org.jboss.arquillian.ce.api.AddRoleToServiceAccountWrapper;
import org.jboss.arquillian.ce.api.OpenShiftResource;
import org.jboss.arquillian.ce.api.OpenShiftResources;
import org.jboss.arquillian.ce.api.RoleBinding;
import org.jboss.arquillian.ce.api.RoleBindings;
import org.jboss.arquillian.ce.utils.StringResolver;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class OpenShiftResourceFactory {
    private static final Logger log = Logger.getLogger(OpenShiftResourceFactory.class.getName());

    public static final String CLASSPATH_PREFIX = "classpath:";
    public static final String ARCHIVE_PREFIX = "archive:";
    public static final String URL_PREFIX = "http";

    private static final OSRFinder OSR_FINDER = new OSRFinder();
    private static final RBFinder RB_FINDER = new RBFinder();
    private static final ARSAFinder ARSA_FINDER = new ARSAFinder();

    public static void createResources(String resourcesKey, OpenShiftAdapter adapter, Archive<?> archive, Class<?> testClass, Properties properties) {
        try {
            final StringResolver resolver = Strings.createStringResolver(properties);

            List<OpenShiftResource> openShiftResources = new ArrayList<>();
            OSR_FINDER.findAnnotations(openShiftResources, testClass);
            for (OpenShiftResource osr : openShiftResources) {
                String file = resolver.resolve(osr.value());

                InputStream stream;
                if (file.startsWith(URL_PREFIX)) {
                    stream = new URL(file).openStream();
                } else if (file.startsWith(CLASSPATH_PREFIX)) {
                    String resourceName = file.substring(CLASSPATH_PREFIX.length());
                    stream = testClass.getClassLoader().getResourceAsStream(resourceName);
                    if (stream == null) {
                        throw new IllegalArgumentException("Could not find resource on classpath: " + resourceName);
                    }
                } else if (file.startsWith(ARCHIVE_PREFIX)) {
                    String resourceName = file.substring(ARCHIVE_PREFIX.length());
                    Node node = archive.get(resourceName);
                    if (node == null) {
                        throw new IllegalArgumentException("Could not find resource in Arquillian archive: " + resourceName);
                    }
                    stream = node.getAsset().openStream();
                } else {
                    stream = new ByteArrayInputStream(file.getBytes());
                }

                log.info(String.format("Creating new OpenShift resource: %s", file));
                adapter.createResource(resourcesKey, stream);
            }
            
            List<RoleBinding> roleBindings = new ArrayList<>();
            RB_FINDER.findAnnotations(roleBindings, testClass);
            for (RoleBinding rb : roleBindings) {
                String roleRefName = resolver.resolve(rb.roleRefName());
                String userName = resolver.resolve(rb.userName());
                log.info(String.format("Adding new role binding: %s / %s", roleRefName, userName));
                adapter.addRoleBinding(resourcesKey, roleRefName, userName);
            }

            List<AddRoleToServiceAccount> arsaBindings = new ArrayList<>();
            ARSA_FINDER.findAnnotations(arsaBindings, testClass);
            for (AddRoleToServiceAccount arsa : arsaBindings) {
                String role = resolver.resolve(arsa.role());
                String saPattern = String.format("system:serviceaccount:${kubernetes.namespace}:%s", arsa.serviceAccount());
                String serviceAccount = resolver.resolve(saPattern);
                log.info(String.format("Adding role %s to service account %s", role, serviceAccount));
                adapter.addRoleBinding(resourcesKey, role, serviceAccount);
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

    private static class ARSAFinder extends Finder<AddRoleToServiceAccountWrapper, AddRoleToServiceAccount> {
        protected Class<AddRoleToServiceAccountWrapper> getWrapperType() {
            return AddRoleToServiceAccountWrapper.class;
        }

        protected Class<AddRoleToServiceAccount> getSingleType() {
            return AddRoleToServiceAccount.class;
        }

        protected AddRoleToServiceAccount[] toSingles(AddRoleToServiceAccountWrapper roleBindings) {
            return roleBindings.value();
        }
    }
}
