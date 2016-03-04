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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.internal.restclient.model.properties.ResourcePropertyKeys;
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
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IProject;
import com.openshift.restclient.model.IReplicationController;
import com.openshift.restclient.model.IResource;
import com.openshift.restclient.model.IService;
import com.openshift.restclient.model.authorization.IRoleBinding;
import com.openshift.restclient.model.project.IProjectRequest;
import com.openshift.restclient.model.template.IParameter;
import com.openshift.restclient.model.template.ITemplate;

import org.jboss.arquillian.ce.adapter.AbstractOpenShiftAdapter;
import org.jboss.arquillian.ce.api.MountSecret;
import org.jboss.arquillian.ce.portfwd.PortForwardContext;
import org.jboss.arquillian.ce.proxy.Proxy;
import org.jboss.arquillian.ce.resources.OpenShiftResourceHandle;
import org.jboss.arquillian.ce.utils.Checker;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.Containers;
import org.jboss.arquillian.ce.utils.CustomValueExpressionResolver;
import org.jboss.arquillian.ce.utils.HookType;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.Port;
import org.jboss.arquillian.ce.utils.RCContext;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class NativeOpenShiftAdapter extends AbstractOpenShiftAdapter {
    private final static Logger log = Logger.getLogger(NativeOpenShiftAdapter.class.getName());

    private final IClient client;

    private Map<String, Collection<IResource>> templates = new ConcurrentHashMap<>();

    public NativeOpenShiftAdapter(Configuration configuration) {
        super(configuration);

        // this is impl detail of DefaultClient
        System.getProperty("osjc.k8e.apiversion", configuration.getApiVersion());
        System.getProperty("osjc.openshift.apiversion", configuration.getApiVersion());

        String token;
        IClient tmpClient = new ClientFactory().create(configuration.getKubernetesMaster(), new NoopSSLCertificateCallback());
        if (configuration.hasOpenshiftBasicAuth()) {
            tmpClient.setAuthorizationStrategy(new BasicAuthorizationStrategy(configuration.getOpenshiftUsername(), configuration.getOpenshiftPassword(), ""));
            IAuthorizationClient authClient = new AuthorizationClientFactory().create(tmpClient);
            IAuthorizationContext context = authClient.getContext(tmpClient.getBaseURL().toString());
            token = context.getToken();
            if (configuration.getToken() != null) {
                log.info("Overriding auth token ...");
            }
            configuration.setToken(token); // re-set token
        } else {
            token = configuration.getToken();
        }
        tmpClient.setAuthorizationStrategy(new TokenAuthorizationStrategy(token));

        this.client = tmpClient;
    }

    protected OpenShiftResourceHandle createResourceFromStream(InputStream stream) throws IOException {
        IResource resource;
        try {
            resource = client.getResourceFactory().create(stream);
        } finally {
            stream.close();
        }
        if (ResourceKind.LIST.equals(resource.getKind())) {
            KubernetesResource kr = (KubernetesResource) resource;
            ModelNode node = kr.getNode();
            String key = node.has(ResourcePropertyKeys.OBJECTS) ? ResourcePropertyKeys.OBJECTS : "items";
            List<IResource> items = new ArrayList<>();
            for (ModelNode item : node.get(key).asList()) {
                IResource ir = client.getResourceFactory().create(item.toJSONString(true));
                items.add(client.create(ir, configuration.getNamespace()));
            }
            return new NativeListOpenShiftResourceHandle(items);
        } else {
            return new NativeOpenShiftResourceHandle(client.create(resource, configuration.getNamespace()));
        }
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

    public PortForwardContext createPortForwardContext(Map<String, String> labels, int port) {
        final List<IPod> pods = client.list(ResourceKind.POD, configuration.getNamespace(), labels);
        if (pods.isEmpty()) {
            throw new IllegalStateException("No such pods: " + labels);
        }
        IPod pod = pods.get(0);
        String nodeName = pod.getHost(); // TODO -- right value?
        return new PortForwardContext(configuration.getKubernetesMaster(), nodeName, configuration.getNamespace(), pod.getName(), port);
    }

    public RegistryLookupEntry lookup() {
        // Grab Docker registry service
        IService service = getService(configuration.getRegistryNamespace(), configuration.getRegistryServiceName());
        String ip = service.getPortalIP();
        int port = service.getPort();
        return new RegistryLookupEntry(ip, String.valueOf(port));
    }

    private Object createProject() {
        // oc new-project <namespace>
        Properties properties = new Properties();
        properties.put("PROJECT_NAME", configuration.getNamespace());
        IProjectRequest pr = createResource(Templates.PROJECT_REQUEST, properties);
        return client.create(pr);
    }

    public boolean checkProject() {
        IProject project = client.get(ResourceKind.PROJECT, configuration.getNamespace(), "");
        return project == null && createProject() != null;
    }

    public boolean deleteProject() {
        IProject project = client.get(ResourceKind.PROJECT, configuration.getNamespace(), "");
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
        // mount secret
        MountSecret ms = context.getMountSecret();
        properties.put("VOLUMES", ms != null ? String.format(Templates.VOLUMES, ms.volumeName(), ms.secretName()) : "[]");
        properties.put("VOLUME_MOUNTS", ms != null ? String.format(Templates.VOLUME_MOUNTS, ms.volumeName(), ms.mountPath()) : "[]");
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

    public Object processTemplateAndCreateResources(String templateKey, String templateURL, List<ParamValue> values) throws Exception {
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

    public Object deleteTemplate(String templateKey) throws Exception {
        Collection<IResource> resources = templates.get(templateKey);
        if (resources != null) {
            for (IResource resource : resources) {
                client.delete(resource);
            }
        }
        return resources;
    }

    public OpenShiftResourceHandle createRoleBinding(String roleRefName, String userName) {
        Properties properties = new Properties();
        properties.setProperty("NAMESPACE", configuration.getNamespace());
        properties.setProperty("ROLE_REF_NAME", roleRefName);
        properties.setProperty("USER_NAME", userName);
        properties.setProperty("SUBJECT_NAME", userName.substring(userName.lastIndexOf(":") + 1));
        IRoleBinding rb = createResource(Templates.ROLE_BINDING, properties);
        return new NativeOpenShiftResourceHandle(client.create(rb, configuration.getNamespace()));
    }

    public IService getService(String namespace, String serviceName) {
        return client.get(ResourceKind.SERVICE, serviceName, namespace);
    }

    @Override
    public void scaleDeployment(final String name, final int replicas) throws DeploymentException {
        final IDeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, name, configuration.getNamespace());
        final Map<String,String> labels = dc.getReplicaSelector();
        final Proxy proxy = createProxy();
        dc.setReplicas(replicas);
        client.update(dc);
        try {
            Containers.delay(0, 4000L, new Checker() {
                public boolean check() {
                    Set<String> pods = proxy.getReadyPods(labels);
                    boolean result = (pods.size() >= replicas);
                    if (result) {
                        log.info(String.format("Pods are ready: %s", pods));
                    }
                    return result;
                }
                @Override
                public String toString() {
                    return String.format("Scaling deployment %s to %d replicas", name, replicas);
                }

            });
        } catch (Exception e) {
            throw new DeploymentException(String.format("Timeout waiting for deployment %s to scale to %s pods", name, replicas), e);
        }
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

    private class NativeOpenShiftResourceHandle implements OpenShiftResourceHandle {
        private IResource resource;

        public NativeOpenShiftResourceHandle(IResource resource) {
            this.resource = resource;
        }

        public void delete() {
            client.delete(resource);
        }
    }

    private class NativeListOpenShiftResourceHandle implements OpenShiftResourceHandle {
        private List<IResource> resources;

        public NativeListOpenShiftResourceHandle(List<IResource> resources) {
            this.resources = resources;
        }

        public void delete() {
            List<RuntimeException> res = new ArrayList<>();
            for (IResource resource : resources) {
                try {
                    client.delete(resource);
                } catch (RuntimeException re) {
                    res.add(re);
                }
            }
            if (res.size() > 0) {
                throw new RuntimeException("Exceptions: " + res);
            }
        }
    }

}
