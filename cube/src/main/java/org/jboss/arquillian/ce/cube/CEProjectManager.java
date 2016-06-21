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

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.DoneableProjectRequest;
import io.fabric8.openshift.api.model.Project;
import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.jboss.arquillian.ce.cube.oauth.OauthInterceptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.AfterSuite;

import java.util.logging.Logger;

/**
 * CEProjectManager
 * <p>
 * Manages the test project, creating it if need be and cleaning up afterward.
 *
 * @author Rob Cernich
 */
public class CEProjectManager {

    private Logger log = Logger.getLogger(CEProjectManager.class.getName());

    private OauthInterceptor oauth = new OauthInterceptor();

    private Project createdProject;


    /**
     * If we're creating the project used to run the tests, we need to do that
     * before Arquillian Cube starts registering Cube objects, so this needs to
     * be invoked prior to CubeOpenShiftRegistrar, but after the OpenShiftClient
     * has been created.
     * <p>
     * TODO: consider creating a new project for each test case.
     */
    public void createProject(@Observes(precedence = 10) OpenShiftClient client, CECubeConfiguration config) throws Exception {

        //token is valid?
        try {
            //just do a request somewhere that requires authentication
            client.getClientExt().inAnyNamespace().oAuthAccessTokens().list();
        } catch (io.fabric8.kubernetes.client.KubernetesClientException e) {
            String actualToken = client.getClientExt().getConfiguration().getOauthToken();
            if (actualToken != null) {
                log.warning(String.format("The token %s has expired, revalidating the token.", actualToken));
                client.getClientExt().getConfiguration().setOauthToken(oauth.getToken(config.getKubernetesMaster(), config.getOpenshiftUsername(), config.getOpenshiftPassword()));
            } else {
                String token = oauth.getToken(config.getKubernetesMaster(), config.getOpenshiftUsername(), config.getOpenshiftPassword());
                throw new KubernetesClientException("Kubeconfig is not initialized, please perform the following command and try again: [oc login --token=" + token +" " +
                        " --server=" + config.getKubernetesMaster() +"]");
            }
        }

        Project existing = null;
        try {
            existing = client.getClientExt().projects().withName(config.getNamespace()).get();
        } catch (Exception e) {
            // f8 barfs if it receives 403 when using auth tokens as it tries to intercept and use null uid/pwd.
        }
        if (existing == null) {
            DoneableProjectRequest projectRequest = client.getClientExt().projectrequests().createNew();
            projectRequest
                    .withDescription("auto-generated project for arquillian testing")
                    .withNewMetadata()
                    .withName(config.getNamespace())
                    .endMetadata();
            projectRequest.done();
            createdProject = client.getClientExt().projects().withName(config.getNamespace()).get();
        }
    }

    /**
     * Clean up after ourselves. This needs to be invoked before the
     * OpenShiftClient is closed.
     */
    public void deleteProject(@Observes(precedence = -100) AfterSuite event, OpenShiftClient client, CECubeConfiguration config) {
        if (createdProject != null && config.performCleanup()) {
            client.getClientExt().projects().withName(createdProject.getMetadata().getName()).delete();
            createdProject = null;
        }
    }
    
}