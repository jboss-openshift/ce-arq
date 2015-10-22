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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.openshift.internal.restclient.ResourceFactory;
import com.openshift.internal.restclient.http.ParameterValue;
import com.openshift.internal.restclient.http.StringParameter;
import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.internal.restclient.model.Service;
import com.openshift.internal.restclient.model.template.Parameter;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.NoopSSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IPort;
import com.openshift.restclient.model.IReplicationController;
import com.openshift.restclient.model.IResource;
import com.openshift.restclient.model.IService;
import com.openshift.restclient.model.project.IProjectRequest;
import com.openshift.restclient.model.template.IParameter;
import com.openshift.restclient.model.template.ITemplate;
import org.jboss.arquillian.ce.utils.AbstractOpenShiftAdapter;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.CustomValueExpressionResolver;
import org.jboss.arquillian.ce.utils.HookType;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.Port;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class NativeOpenShiftAdapter extends AbstractOpenShiftAdapter {
    private final static Logger log = Logger.getLogger(NativeOpenShiftAdapter.class.getName());

    private final IClient client;

    private Map<String, Object> configs = new HashMap<>();
    private IProjectRequest projectRequest;

    public NativeOpenShiftAdapter(Configuration configuration) {
        super(configuration);

        // this is impl detail of DefaultClient
        System.getProperty("osjc.k8e.apiversion", configuration.getApiVersion());
        System.getProperty("osjc.openshift.apiversion", configuration.getApiVersion());

        this.client = new ClientFactory().create(configuration.getKubernetesMaster(), new NoopSSLCertificateCallback());
    }

    <T extends IResource> T createResource(String template, Properties properties) {
        final ValueExpressionResolver resolver = new CustomValueExpressionResolver(properties);
        ValueExpression expression = new ValueExpression(template);
        String config = expression.resolveString(resolver);
        return client.getResourceFactory().create(config);
    }

    IParameter createParameter(String name, String value) {
        ModelNode node = new ModelNode();
        node.get("name").set(name);
        Parameter parameter = new Parameter(node);
        parameter.setValue(value);
        return parameter;
    }

    public RegistryLookupEntry lookup() {
        // Grab Docker registry service
        IService service = getService(configuration.getRegistryNamespace(), configuration.getRegistryServiceName());
        String ip = service.getPortalIP();
        int port = service.getPort();
        return new RegistryLookupEntry(ip, String.valueOf(port));
    }

    public IProjectRequest createProject(String namespace) {
        // oc new-project <namespace>
        projectRequest = client.create(ResourceKind.PROJECT_REQUEST, "default", configuration.getNamespace(), null, null);
        // client.inNamespace(namespace).policyBindings().createNew().withNewMetadata().withName(configuration.getPolicyBinding()).endMetadata().done();

        // oc policy add-role-to-user admin admin -n <namespace>
        // TODO

        return null;
    }

    public boolean deleteProject(String namespace) {
        client.delete(projectRequest);
        return true;
    }

    public String deployReplicationController(String name, Map<String, String> deploymentLabels, String imageName, List<Port> ports, int replicas, HookType hookType, String preStopPath, boolean ignorePreStop) throws Exception {
        return null;
    }

    public Object processTemplateAndCreateResources(String name, String templateURL, String namespace, List<ParamValue> values) throws Exception {
        ITemplate template;
        try (InputStream stream = new URL(templateURL).openStream()) {
            template = client.getResourceFactory().create(stream);
        }

        Collection<IParameter> parameters = new HashSet<>();
        for (ParamValue pv : values) {
            parameters.add(createParameter(pv.getName(), pv.getValue()));
        }
        template.updateParameterValues(parameters);

        return null;
    }

    public Object deleteTemplate(String name, String namespace) throws Exception {
        return null;
    }

    public IService getService(String namespace, String serviceName) {
        return client.get(ResourceKind.SERVICE, serviceName, namespace);
    }

    public void cleanReplicationControllers(String... ids) throws Exception {
        for (String id : ids) {
            try {
                IReplicationController rc = client.get(ResourceKind.REPLICATION_CONTROLLER, id, configuration.getNamespace());
                client.delete(rc);
                log.info(String.format("RC [%s] delete.", id));
            } catch (Exception e) {
                log.log(Level.WARNING, String.format("Exception while deleting RC [%s]: %s", id, e), e);
            }
        }
    }

    public void cleanPods(Map<String, String> labels) throws Exception {
        final List<IPod> pods = client.list(ResourceKind.POD, configuration.getNamespace(), labels);
        try {
            for (IPod pod : pods) {
                client.delete(pod);
                log.info(String.format("Pod [%s] delete.", pod.getName()));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception while deleting pod [%s]: %s", labels, e), e);
        }
    }

    private String deployReplicationController(IReplicationController rc) throws Exception {
        return client.create(rc, configuration.getNamespace()).getName();
    }

    public void close() throws IOException {
    }
}
