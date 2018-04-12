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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.api.AddRoleToServiceAccount;
import org.jboss.arquillian.ce.api.AddRoleToServiceAccountWrapper;
import org.jboss.arquillian.ce.api.OpenShiftImageStreamResource;
import org.jboss.arquillian.ce.api.OpenShiftResource;
import org.jboss.arquillian.ce.api.OpenShiftResources;
import org.jboss.arquillian.ce.api.RoleBinding;
import org.jboss.arquillian.ce.api.RoleBindings;
import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.ce.api.TemplateResources;
import org.jboss.arquillian.ce.utils.StringResolver;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

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
    private static final TEMPFinder TEMP_FINDER = new TEMPFinder();

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

            // This part of the code is responsible for deploying image streams.
            // Usually when we run a test we want to use a custom image to test it with. Images
            // are defined in image streams so we need to edit the main image stream and change the
            // the image definition.
            if (testClass.isAnnotationPresent(OpenShiftImageStreamResource.class)) {
                OpenShiftImageStreamResource imageStream = testClass.getAnnotation(OpenShiftImageStreamResource.class);

                // First of all resolve any properties
                String url = resolver.resolve(imageStream.url());
                String image = resolver.resolve(imageStream.image());
                boolean insecure = Boolean.valueOf(resolver.resolve(imageStream.insecure()));

                JSONParser parser = new JSONParser();

                // Parse the image stream
                JSONObject definition = (JSONObject) parser.parse(new BufferedReader(new InputStreamReader(new URL(url).openStream())));
                JSONArray items = (JSONArray) definition.get("items");

                if (items.size() > 1) {
                    throw new IllegalArgumentException("Multiple image streams defined in single file are not supported");
                }

                JSONObject is = (JSONObject) items.get(0);
                JSONObject tags = (JSONObject) ((JSONArray) ((JSONObject) is.get("spec")).get("tags")).get(0);
                JSONObject from = (JSONObject) tags.get("from");

                // Finally, replace the image name
                from.put("name", image);

                // Make sure the importPolicy is set correctly
                Map<String, Object> importPolicy = new HashMap<>();
                importPolicy.put("insecure", insecure);
                tags.put("importPolicy", new JSONObject(importPolicy));

                // Add the 'openshift.io/image.insecureRepository' annotation as well
                JSONObject annotations = (JSONObject) ((JSONObject) is.get("metadata")).get("annotations");
                annotations.put("openshift.io/image.insecureRepository", insecure);

                log.info(String.format("Creating new OpenShift image stream resource from %s", url));
                log.info(definition.toJSONString());

                adapter.createResource(resourcesKey, new ByteArrayInputStream(definition.toJSONString().getBytes()));
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

    /**
     * Aggregates a list of templates specified by @Template
     */
    public static List<Template> getTemplates(Class<?> testClass) {
        try {
            List<Template> templates = new ArrayList<>();
            TEMP_FINDER.findAnnotations(templates, testClass);
            return templates;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns true if templates are to be instantiated synchronously and false if
     * asynchronously.
     */
    public static boolean syncInstantiation(Class<?> testClass) {
        List<Template> templates = new ArrayList<>();
        TemplateResources tr = TEMP_FINDER.findAnnotations(templates, testClass);
        if (tr == null) {
            /* Default to synchronous instantiation */
            return true;
        } else {
            return tr.syncInstantiation();
        }
    }

    public static void deleteResources(String resourcesKey, OpenShiftAdapter adapter) {
        try {
            adapter.deleteResources(resourcesKey);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static abstract class Finder<U extends Annotation, V extends Annotation> {

        protected abstract Class<U> getWrapperType();

        protected abstract Class<V> getSingleType();

        protected abstract V[] toSingles(U u);

        U findAnnotations(List<V> annotations, Class<?> testClass) {
            if (testClass == Object.class) {
                return null;
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
            return anns;
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

    private static class TEMPFinder extends Finder<TemplateResources, Template> {
        protected Class<TemplateResources> getWrapperType() {
            return TemplateResources.class;
        }

        protected Class<Template> getSingleType() {
            return Template.class;
        }

        protected Template[] toSingles(TemplateResources templateResources) {
            return templateResources.templates();
        }

        protected boolean syncInstantiation(TemplateResources templateResources) {
            return templateResources.syncInstantiation();
        }
    }
}
