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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.openshift.internal.restclient.model.template.Parameter;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.NoopSSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.AuthorizationClientFactory;
import com.openshift.restclient.authorization.BasicAuthorizationStrategy;
import com.openshift.restclient.authorization.IAuthorizationClient;
import com.openshift.restclient.authorization.IAuthorizationContext;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.resources.IProjectTemplateProcessing;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IProject;
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
import org.jboss.arquillian.ce.utils.Proxy;
import org.jboss.arquillian.ce.utils.RCContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class NativeOpenShiftAdapter extends AbstractOpenShiftAdapter {
    private final static Logger log = Logger.getLogger(NativeOpenShiftAdapter.class.getName());

    private final IClient client;

    private Map<String, Collection<IResource>> templates = new HashMap<>();

    public NativeOpenShiftAdapter(Configuration configuration) {
        super(configuration);

        // this is impl detail of DefaultClient
        System.getProperty("osjc.k8e.apiversion", configuration.getApiVersion());
        System.getProperty("osjc.openshift.apiversion", configuration.getApiVersion());

        IClient tmpClient = new ClientFactory().create(configuration.getKubernetesMaster(), new NoopSSLCertificateCallback());
        tmpClient.setAuthorizationStrategy(new BasicAuthorizationStrategy(configuration.getOpenshiftUsername(), configuration.getDockerPassword(), ""));
        IAuthorizationClient authClient = new AuthorizationClientFactory().create(tmpClient);
        IAuthorizationContext context = authClient.getContext(tmpClient.getBaseURL().toString());
        String token = context.getToken();
        tmpClient.setAuthorizationStrategy(new TokenAuthorizationStrategy(token));
        if (configuration.getToken() != null) {
            log.info("Overriding auth token ...");
        }
        configuration.setToken(token); // re-set token

        this.client = tmpClient;
    }

    <T extends IResource> T createResource(String json, Properties properties) {
        String template = Templates.readJson(configuration.getApiVersion(), json);
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

    public Proxy createProxy() {
        return new NativeProxy(configuration, client);
    }

    public RegistryLookupEntry lookup() {
        // Grab Docker registry service
        IService service = getService(configuration.getRegistryNamespace(), configuration.getRegistryServiceName());
        String ip = service.getPortalIP();
        int port = service.getPort();
        return new RegistryLookupEntry(ip, String.valueOf(port));
    }

    public Object createProject(String namespace) {
        // oc new-project <namespace>
        Properties properties = new Properties();
        properties.put("PROJECT_NAME", namespace);
        IProjectRequest pr = createResource(Templates.PROJECT_REQUEST, properties);
        return client.create(pr);
    }

    public boolean deleteProject(String namespace) {
        IProject project = client.get(ResourceKind.PROJECT, namespace, "");
        client.delete(project);
        return true;
    }

    public String deployPod(String name, String env, RCContext context) throws Exception {
        Properties properties = getResourceProperties(name, env, context);
        IPod rc = createResource(Templates.POD, properties);
        return client.create(rc, configuration.getNamespace()).getName();
    }

    public String deployReplicationController(String name, String env, RCContext context) throws Exception {
        Properties properties = getResourceProperties(name, env, context);
        IReplicationController rc = createResource(Templates.REPLICATION_CONTROLLER, properties);
        return client.create(rc, configuration.getNamespace()).getName();
    }

    private Properties getResourceProperties(String name, String env, RCContext context) {
        Properties properties = new Properties();
        properties.put("NAMESPACE", configuration.getNamespace());
        properties.put("NAME", name);
        Map<String, String> labels = Collections.singletonMap("name", name + "Controller");
        properties.put("TOP_LABELS", toLabels(labels));
        Map<String, String> podLabels = new HashMap<>(context.getLabels());
        podLabels.put("name", name + "-pod");
        properties.put("POD_LABELS", toLabels(podLabels));
        properties.put("REPLICAS", String.valueOf(context.getReplicas()));
        properties.put("POD_NAME", name + "-pod");
        properties.put("CONTAINER_NAME", name + "-container");
        properties.put("IMAGE_NAME", context.getImageName());
        properties.put("IMAGE_PULL_POLICY", configuration.getImagePullPolicy());
        properties.put("PROBE", createProbe(env, context.getProbeHook(), context.getProbeCommands()));
        properties.put("LIFECYCLE", createLifecycle(env, context.getLifecycleHook(), context.getPreStopPath(), context.isIgnorePreStop()));
        properties.put("PORTS", toPorts(context.getPorts()));
        return properties;
    }

    private String createProbe(String env, HookType probeHook, List<String> probeCommands) {
        if (probeCommands != null && probeCommands.size() > 0 && probeHook != null) {
            return Templates.readJson(configuration.getApiVersion(), Templates.READINESS_PROBE, env);
        } else {
            return "";
        }
    }

    private String createLifecycle(String env, HookType hookType, String preStopPath, boolean ignorePreStop) {
        if (!ignorePreStop && hookType != null && preStopPath != null) {
            return Templates.readJson(configuration.getApiVersion(), Templates.LIFECYCLE, env);
        } else {
            return "";
        }
    }

    private String toLabels(Map<String, String> labels) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = labels.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append(String.format("\"%s\" : \"%s\"", entry.getKey(), entry.getValue()));
            if (iterator.hasNext()) {
                builder.append(",");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String toPorts(List<Port> ports) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ports.size(); i++) {
            Port port = ports.get(i);
            builder.append(String.format("{\"name\" : \"%s\", \"containerPort\" : %s}", port.getName(), port.getContainerPort()));
            if (i < ports.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public Object processTemplateAndCreateResources(String templateKey, String templateURL, String namespace, List<ParamValue> values) throws Exception {
        final IProject project = client.get(ResourceKind.PROJECT, configuration.getNamespace(), "");

        final ITemplate template;
        try (InputStream stream = new URL(templateURL).openStream()) {
            template = client.getResourceFactory().create(stream);
        }

        Collection<IParameter> parameters = new HashSet<>();
        for (ParamValue pv : values) {
            parameters.add(createParameter(pv.getName(), pv.getValue()));
        }
        template.updateParameterValues(parameters);

        final IProjectTemplateProcessing capability = project.getCapability(IProjectTemplateProcessing.class);
        ITemplate processed = capability.process(template);
        Collection<IResource> resources = capability.apply(processed);
        templates.put(templateKey, resources);
        return resources;
    }

    public Object deleteTemplate(String templateKey, String namespace) throws Exception {
        Collection<IResource> resources = templates.get(templateKey);
        if (resources != null) {
            for (IResource resource : resources) {
                client.delete(resource);
            }
        }
        return resources;
    }

    public IService getService(String namespace, String serviceName) {
        return client.get(ResourceKind.SERVICE, serviceName, namespace);
    }

    public void cleanReplicationControllers(String... ids) throws Exception {
        List<IReplicationController> rcs = client.list(ResourceKind.REPLICATION_CONTROLLER, configuration.getNamespace());
        for (IReplicationController rc : rcs) {
            for (String id : ids) {
                if (rc.getName().equals(id)) {
                    try {
                        client.delete(rc);
                        log.info(String.format("RC [%s] delete.", id));
                    } catch (Exception e) {
                        log.log(Level.WARNING, String.format("Exception while deleting RC [%s]: %s", id, e), e);
                    }
                }
            }
        }
    }

    public void cleanPods(Map<String, String> labels) throws Exception {
        final List<IPod> pods = client.list(ResourceKind.POD, configuration.getNamespace(), labels);
        for (IPod pod : pods) {
            try {
                client.delete(pod);
                log.info(String.format("Pod [%s] delete.", pod.getName()));
            } catch (Exception e) {
                log.log(Level.WARNING, String.format("Exception while deleting pod [%s]: %s", pod, e), e);
            }
        }
    }

    public void close() throws IOException {
        templates.clear();
    }

}
