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

import static org.jboss.arquillian.ce.utils.TemplateUtils.addParameterValues;
import static org.jboss.arquillian.ce.utils.TemplateUtils.executeProcessTemplate;
import static org.jboss.arquillian.ce.utils.TemplateUtils.readLabels;
import static org.jboss.arquillian.ce.utils.TemplateUtils.readParameters;
import static org.jboss.arquillian.ce.utils.TemplateUtils.readReplicas;
import static org.jboss.arquillian.ce.utils.TemplateUtils.readTemplateUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.ce.api.model.DeploymentConfig;
import org.jboss.arquillian.ce.api.model.OpenShiftResource;
import org.jboss.arquillian.ce.cube.dns.CENameService;
import org.jboss.arquillian.ce.resources.OpenShiftResourceFactory;
import org.jboss.arquillian.ce.utils.Operator;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.ReflectionUtils;
import org.jboss.arquillian.ce.utils.StringResolver;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.event.container.AfterStart;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.annotation.ClassScoped;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

/**
 * CEEnvironmentProcessor
 * <p>
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
        List<? extends OpenShiftResource> getResources();
    }

    @Inject
    @ClassScoped
    private InstanceProducer<TemplateDetails> templateDetailsProducer;

    /**
     * Create the environment as specified by @Template or
     * arq.extension.ce-cube.openshift.template.* properties.
     * <p>
     * In the future, this might be handled by starting application Cube
     * objects, e.g. CreateCube(application), StartCube(application)
     * <p>
     * Needs to fire before the containers are started.
     */
    public void createEnvironment(@Observes(precedence = 10) BeforeClass event, OpenShiftAdapter client,
                                  CECubeConfiguration configuration, OpenShiftClient openshiftClient) throws DeploymentException {
        final TestClass testClass = event.getTestClass();

        log.info(String.format("Creating environment for %s", testClass.getName()));
        OpenShiftResourceFactory.createResources(testClass.getName(), client, null, testClass.getJavaClass(), configuration.getProperties());
        processTemplate(testClass, client, configuration);
        registerRoutes(configuration, openshiftClient);

    }

    /**
     * Wait for the template resources to come up after the test container has
     * been started. This allows the test container and the template resources
     * to come up in parallel.
     */
    public void waitForDeployments(@Observes(precedence = -100) AfterStart event, OpenShiftAdapter client, TemplateDetails details, TestClass testClass) throws Exception {
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
            delay(client, details.getResources());
        } catch (Throwable t) {
            throw new DeploymentException("Error waiting for template resources to deploy: " + testClass.getName(), t);
        }
    }

    /**
     * Tear down the environment.
     * <p>
     * In the future, this might be handled by stopping application Cube
     * objects, e.g. StopCube(application), DestroyCube(application).
     */
    public void deleteEnvironment(@Observes(precedence = -10) AfterClass event, OpenShiftAdapter client, CECubeConfiguration configuration, TemplateDetails details) throws Exception {
        final TestClass testClass = event.getTestClass();
        if (configuration.performCleanup()) {
            log.info(String.format("Deleting environment for %s", testClass.getName()));
            client.deleteTemplate(testClass.getName());
            OpenShiftResourceFactory.deleteResources(testClass.getName(), client);
            additionalCleanup(client, Collections.singletonMap("test-case", testClass.getJavaClass().getSimpleName().toLowerCase()));
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
        final List<? extends OpenShiftResource> resources;

        if (templateURL == null || templateURL.length() == 0) {
            log.warning(String.format("No template specified for %s", tc.getName()));
            return;
        }

        try {
            final int replicas = readReplicas(tc);
            final Map<String, String> labels = readLabels(template, configuration, resolver);
            labels.put("test-case", tc.getJavaClass().getSimpleName().toLowerCase());
            if (labels.isEmpty()) {
                log.warning(String.format("Empty labels for template: %s, namespace: %s", templateURL,
                        configuration.getNamespace()));
            }

            if (executeProcessTemplate(template, configuration)) {
                List<ParamValue> values = new ArrayList<>();
                addParameterValues(values, readParameters(template, configuration, resolver), false);
                addParameterValues(values, System.getenv(), true);
                addParameterValues(values, System.getProperties(), true);
                values.add(new ParamValue("REPLICAS", String.valueOf(replicas))); // not yet supported

                log.info(String.format("Applying OpenShift template: %s", templateURL));
                // use old archive name as templateKey
                resources = client.processTemplateAndCreateResources(tc.getName(), templateURL, values, labels);
            } else {
                log.info(String.format("Ignoring template [%s] processing ...", templateURL));
                resources = Collections.emptyList();
            }

            templateDetailsProducer.set(new TemplateDetails() {
                @Override
                public List<? extends OpenShiftResource> getResources() {
                    return resources;
                }
            });
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy template: " + templateURL, t);
        }
    }

    private void delay(OpenShiftAdapter client, final List<? extends OpenShiftResource> resources) throws Exception {
        for (OpenShiftResource resource : resources) {
            if (resource instanceof DeploymentConfig) {
                final DeploymentConfig dc = (DeploymentConfig) resource;
                client.delay(dc.getSelector(), dc.getReplicas(), Operator.EQUAL);
            }
        }
    }

    private void additionalCleanup(OpenShiftAdapter client, Map<String, String> labels) throws Exception {
        client.cleanRemnants(labels);
    }

}
