/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other
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
package org.jboss.arquillian.ce.cube;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.api.Replicas;
import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.ce.api.TemplateParameter;
import org.jboss.arquillian.ce.cube.dns.CENameService;
import org.jboss.arquillian.ce.resources.OpenShiftResourceFactory;
import org.jboss.arquillian.ce.utils.Operator;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.ReflectionUtils;
import org.jboss.arquillian.ce.utils.StringResolver;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.event.container.AfterStart;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.annotation.ClassScoped;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

/**
 * CEEnvironmentProcessor
 * <p/>
 * Temporary class to handle @Template and @OpenShiftResource annotations on
 * test classes. Eventually, these will be migrated to Cube types, at which
 * point this will delegate to those for setup/teardown (via
 * StartCube/StopCube).
 * 
 * @author Rob Cernich
 */
public class CEEnvironmentProcessor {

    private final Logger log = Logger.getLogger(CEEnvironmentProcessor.class.getName());
    
    public interface TemplateDetails {
        public Map<String,String> getLabels();
        public int getReplicas();
    }

    @Inject @ClassScoped
    private InstanceProducer<TemplateDetails> templateDetailsProducer;

    /**
     * Create the environment as specified by @Template or
     * arq.extension.ce-cube.openshift.template.* properties.
     * 
     * In the future, this might be handled by starting application Cube
     * objects, e.g. CreateCube(application), StartCube(application)
     * 
     * Needs to fire before the containers are started.
     */
    public void createEnvironment(@Observes(precedence=10) BeforeClass event, OpenShiftAdapter client,
            CECubeConfiguration configuration, OpenShiftClient openshiftClient) throws DeploymentException {
        final TestClass testClass = event.getTestClass();
        log.info(String.format("Creating environment for %s", testClass.getName()));
        OpenShiftResourceFactory.createResources(testClass.getName(), client, null, testClass.getJavaClass(),
                configuration.getProperties());
        processTemplate(testClass, client, configuration);
        registerRoutes(configuration, openshiftClient);
    }

    /**
     * Wait for the template resources to come up after the test container has
     * been started. This allows the test container and the template resources
     * to come up in parallel.
     */
    public void waitForDeployments(@Observes(precedence = -100) AfterStart event, OpenShiftAdapter client,
            TemplateDetails details, TestClass testClass) throws Exception {
        if (testClass == null) {
            // nothing to do, since we're not in ClassScoped context
            return;
        }
        if (details == null) {
            log.warning(String.format("No environment for %s", testClass.getName()));
            return;
        }
        log.info(String.format("Waiting for environment for %s", testClass.getName()));
        try {
            client.delay(details.getLabels(), details.getReplicas(), Operator.GREATER_THAN_OR_EQUAL);
        } catch (Throwable t) {
            throw new DeploymentException("Error waiting for template resources to deploy: " + testClass.getName(), t);
        }
    }

    /**
     * Tear down the environment.
     * 
     * In the future, this might be handled by stopping application Cube
     * objects, e.g. StopCube(application), DestroyCube(application).
     */
    public void deleteEnvironment(@Observes AfterClass event, CECubeConfiguration configuration, OpenShiftAdapter client) throws Exception {
        final TestClass testClass = event.getTestClass();
        if (configuration.performCleanup()) {
            log.info(String.format("Deleting environment for environment for %s", testClass.getName()));
            log.info(configuration.toString());
            client.deleteTemplate(testClass.getName());
            OpenShiftResourceFactory.deleteResources(testClass.getName(), client);
        } else {
            log.info(String.format("Ignoring cleanup for %s", testClass.getName()));
        }
    }

    private void registerRoutes(CECubeConfiguration configuration, OpenShiftClient client) {
        CENameService.setRoutes(client.getClientExt().routes().list(), configuration.getRouterHost());
    }

    private void processTemplate(TestClass tc, OpenShiftAdapter client, CECubeConfiguration configuration)
            throws DeploymentException {
        final StringResolver resolver = Strings.createStringResolver(configuration.getProperties());
        final Template template = ReflectionUtils.findAnnotation(tc.getJavaClass(), Template.class);
        final String templateURL = readTemplateUrl(template, configuration, resolver);
        try {
            final int replicas = readReplicas(tc);
            final Map<String, String> labels = readLabels(template, configuration, resolver);
            if (labels.isEmpty()) {
                log.warning(String.format("Empty labels for template: %s, namespace: %s", templateURL,
                        configuration.getNamespace()));
            }

            if (executeProcessTemplate(template, configuration)) {
                List<ParamValue> values = new ArrayList<>();
                addParameterValues(values, readParameters(template, configuration, resolver), false);
                addParameterValues(values, System.getenv(), true);
                addParameterValues(values, System.getProperties(), true);
                values.add(new ParamValue("REPLICAS", String.valueOf(replicas))); // not
                                                                                  // yet
                                                                                  // supported

                log.info(String.format("Applying OpenShift template: %s", templateURL));
                // use old archive name as templateKey
                client.processTemplateAndCreateResources(tc.getName(), templateURL, values);
            } else {
                log.info(String.format("Ignoring template [%s] processing ...", templateURL));
            }

            templateDetailsProducer.set(new TemplateDetails() {
                
                @Override
                public int getReplicas() {
                    return replicas;
                }
                
                @Override
                public Map<String, String> getLabels() {
                    return labels;
                }
            });
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy template: " + templateURL, t);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked" })
    private void addParameterValues(List<ParamValue> values, Map map, boolean filter) {
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

    private String readTemplateUrl(Template template, CECubeConfiguration configuration, StringResolver resolver) {
        String templateUrl = template == null ? null : template.url();
        if (templateUrl == null || templateUrl.length() == 0) {
            templateUrl = resolver.resolve(configuration.getTemplateURL());
        }

        if (templateUrl == null) {
            throw new IllegalArgumentException(
                    "Missing template URL! Either add @Template to your test or add -Dopenshift.template.url=<url>");
        }

        return templateUrl;
    }

    private int readReplicas(TestClass testClass) {
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
                throw new IllegalArgumentException(String.format(
                        "Node / pod index bigger then replicas; %s >= %s ! (%s)", index, r, c));
            }
            max = Math.max(max, index);
        }
        if (r < 0) {
            return max + 1;
        } else {
            return r;
        }
    }

    private Map<String, String> readLabels(Template template, CECubeConfiguration configuration, StringResolver resolver) {
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

    private boolean executeProcessTemplate(Template template, CECubeConfiguration configuration) {
        return (template == null || template.process()) && configuration.isTemplateProcess();
    }

    private Map<String, String> readParameters(Template template, CECubeConfiguration configuration,
            StringResolver resolver) {
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
