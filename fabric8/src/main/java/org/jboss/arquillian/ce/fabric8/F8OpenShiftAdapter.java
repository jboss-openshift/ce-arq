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

package org.jboss.arquillian.ce.fabric8;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.Handler;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.openshift.api.model.WebHookTriggerBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.openshift.client.ParameterValue;
import org.jboss.arquillian.ce.utils.AbstractOpenShiftAdapter;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.HookType;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.Port;
import org.jboss.arquillian.ce.utils.RCContext;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class F8OpenShiftAdapter extends AbstractOpenShiftAdapter {
    private final static Logger log = Logger.getLogger(F8OpenShiftAdapter.class.getName());

    private final OpenShiftClient client;
    private Map<String, KubernetesList> templates = new HashMap<>();

    static OpenShiftClient create(Configuration configuration) {
        OpenShiftConfig config = new OpenShiftConfigBuilder()
            .withMasterUrl(configuration.getKubernetesMaster())
            .withTrustCerts(configuration.isTrustCerts())
            .build();

        return new DefaultOpenShiftClient(config);
    }

    public F8OpenShiftAdapter(Configuration configuration) {
        super(configuration);
        this.client = create(configuration);
    }

    public RegistryLookupEntry lookup() {
        // Grab Docker registry service
        Service service = getService(configuration.getRegistryNamespace(), configuration.getRegistryServiceName());
        ServiceSpec spec = service.getSpec();
        String ip = spec.getClusterIP();
        if (ip == null) {
            ip = spec.getPortalIP();
        }
        Integer port = findHttpServicePort(spec.getPorts());
        return new RegistryLookupEntry(ip, String.valueOf(port));
    }

    public Object createProject(String namespace) {
        // oc new-project <namespace>
        return client.projectrequests().createNew().withNewMetadata().withName(namespace).endMetadata().done();
    }

    public boolean deleteProject(String namespace) {
        return client.projects().withName(namespace).delete();
    }

    public String deployPod(String name, String env, RCContext context) throws Exception {
        List<Container> containers = getContainers(name, context);

        PodSpec podSpec = new PodSpec();
        podSpec.setContainers(containers);

        Map<String, String> podLabels = new HashMap<>();
        podLabels.put("name", name + "-pod");
        podLabels.putAll(context.getLabels());

        ObjectMeta metadata = new ObjectMeta();
        metadata.setLabels(podLabels);

        Pod pod = new Pod();
        pod.setApiVersion(Pod.ApiVersion.fromValue(configuration.getApiVersion()));
        pod.setMetadata(metadata);
        pod.setSpec(podSpec);

        return client.pods().inNamespace(configuration.getNamespace()).create(pod).getMetadata().getName();
    }

    public String deployReplicationController(String name, String env, RCContext context) throws Exception {
        List<Container> containers = getContainers(name, context);

        Map<String, String> podLabels = new HashMap<>();
        podLabels.put("name", name + "-pod");
        podLabels.putAll(context.getLabels());
        PodTemplateSpec podTemplate = createPodTemplateSpec(podLabels, containers);

        Map<String, String> selector = Collections.singletonMap("name", name + "-pod");
        Map<String, String> labels = Collections.singletonMap("name", name + "Controller");
        ReplicationController rc = createReplicationController(name + "rc", configuration.getApiVersion(), labels, context.getReplicas(), selector, podTemplate);

        return client.replicationControllers().inNamespace(configuration.getNamespace()).create(rc).getMetadata().getName();
    }

    private List<Container> getContainers(String name, RCContext context) throws Exception {
        List<EnvVar> envVars = Collections.emptyList();

        List<ContainerPort> cps = new ArrayList<>();
        for (Port port : context.getPorts()) {
            ContainerPort cp = new ContainerPort();
            cp.setName(port.getName());
            cp.setContainerPort(port.getContainerPort());
            cps.add(cp);
        }

        List<VolumeMount> volumes = Collections.emptyList();

        Lifecycle lifecycle = null;
        if (!context.isIgnorePreStop() && context.getLifecycleHook() != null && context.getPreStopPath() != null) {
            lifecycle = new Lifecycle();
            Handler preStopHandler = createHandler(context.getLifecycleHook(), context.getPreStopPath(), cps);
            lifecycle.setPreStop(preStopHandler);
        }

        Probe probe = null;
        if (context.getProbeCommands() != null && context.getProbeCommands().size() > 0 && context.getProbeHook() != null) {
            probe = new Probe();
            handleProbe(probe, context.getProbeHook(), context.getProbeCommands(), cps);
        }

        Container container = createContainer(context.getImageName(), name + "-container", envVars, cps, volumes, lifecycle, probe, configuration.getImagePullPolicy());

        return Collections.singletonList(container);
    }

    private Handler createHandler(HookType hookType, String preStopPath, List<ContainerPort> ports) {
        Handler preStopHandler = new Handler();
        switch (hookType) {
            case HTTP_GET:
                HTTPGetAction httpGet = new HTTPGetAction();
                httpGet.setPath(preStopPath);
                httpGet.setPort(findHttpContainerPort(ports));
                preStopHandler.setHttpGet(httpGet);
                break;
            case EXEC:
                ExecAction exec = new ExecAction(Collections.singletonList(preStopPath));
                preStopHandler.setExec(exec);
                break;
            default:
                throw new IllegalArgumentException("Unsupported hook type: " + hookType);
        }
        return preStopHandler;
    }

    private void handleProbe(Probe probe, HookType hookType, List<String> probeCommands, List<ContainerPort> ports) {
        switch (hookType) {
            case HTTP_GET:
                HTTPGetAction httpGet = new HTTPGetAction();
                httpGet.setPath(probeCommands.get(0));
                httpGet.setPort(findHttpContainerPort(ports));
                probe.setHttpGet(httpGet);
                break;
            case EXEC:
                ExecAction exec = new ExecAction(probeCommands);
                probe.setExec(exec);
                break;
            default:
                throw new IllegalArgumentException("Unsupported hook type: " + hookType);
        }
    }

    public KubernetesList processTemplateAndCreateResources(String templateKey, String templateURL, String namespace, List<ParamValue> values) throws Exception {
        List<ParameterValue> pvs = new ArrayList<>();
        for (ParamValue value : values) {
            pvs.add(new ParameterValue(value.getName(), value.getValue()));
        }
        KubernetesList list = processTemplate(templateURL, namespace, pvs);
        KubernetesList result = createResources(namespace, list);
        templates.put(templateKey, result);
        return result;
    }

    private KubernetesList processTemplate(String templateURL, String namespace, List<ParameterValue> values) throws IOException {
        try (InputStream stream = new URL(templateURL).openStream()) {
            return client.templates().inNamespace(namespace).load(stream).process(values.toArray(new ParameterValue[values.size()]));
        }
    }

    private KubernetesList createResources(String namespace, KubernetesList list) {
        return client.lists().inNamespace(namespace).create(list);
    }

    private Object triggerBuild(String namespace, String buildName, String secret, String type) throws Exception {
        return client.buildConfigs().inNamespace(namespace).withName(buildName).withSecret(secret).withType(type).trigger(new WebHookTriggerBuilder().withSecret(secret).build());
    }

    public Object deleteTemplate(String templateKey, String namespace) throws Exception {
        KubernetesList config = templates.get(templateKey);
        if (config != null) {
            return client.lists().inNamespace(namespace).delete(config);
        }
        return config;
    }

    private String deployService(String name, String apiVersion, String portName, int port, int containerPort, Map<String, String> selector) throws Exception {
        Service service = new Service();

        service.setApiVersion(Service.ApiVersion.fromValue(apiVersion));

        ObjectMeta objectMeta = new ObjectMeta();
        service.setMetadata(objectMeta);
        objectMeta.setName(name);

        ServiceSpec spec = new ServiceSpec();
        service.setSpec(spec);

        ServicePort sp = new ServicePort();
        sp.setName(portName);
        sp.setPort(port);
        sp.setTargetPort(new IntOrString(containerPort));
        spec.setPorts(Collections.singletonList(sp));

        spec.setSelector(selector);

        return client.services().inNamespace(configuration.getNamespace()).create(service).getMetadata().getName();
    }

    public Service getService(String namespace, String serviceName) {
        return client.services().inNamespace(namespace).withName(serviceName).get();
    }

    private Container createContainer(String image, String name, List<EnvVar> envVars, List<ContainerPort> ports, List<VolumeMount> volumes, Lifecycle lifecycle, Probe probe, String imagePullPolicy) throws Exception {
        Container container = new Container();
        container.setImage(image);
        container.setName(name);
        container.setEnv(envVars);
        container.setPorts(ports);
        container.setVolumeMounts(volumes);
        container.setLifecycle(lifecycle);
        container.setReadinessProbe(probe);
        container.setImagePullPolicy(imagePullPolicy);
        return container;
    }

    private PodTemplateSpec createPodTemplateSpec(Map<String, String> labels, List<Container> containers) throws Exception {
        PodTemplateSpec pts = new PodTemplateSpec();

        ObjectMeta objectMeta = new ObjectMeta();
        pts.setMetadata(objectMeta);
        objectMeta.setLabels(labels);

        PodSpec ps = new PodSpec();
        pts.setSpec(ps);
        ps.setContainers(containers);

        return pts;
    }

    private ReplicationController createReplicationController(String name, String apiVersion, Map<String, String> labels, int replicas, Map<String, String> selector, PodTemplateSpec podTemplate) throws Exception {
        ReplicationController rc = new ReplicationController();

        rc.setApiVersion(ReplicationController.ApiVersion.fromValue(apiVersion));

        ObjectMeta objectMeta = new ObjectMeta();
        rc.setMetadata(objectMeta);
        objectMeta.setName(name);
        objectMeta.setLabels(labels);

        ReplicationControllerSpec spec = new ReplicationControllerSpec();
        rc.setSpec(spec);
        spec.setReplicas(replicas);
        spec.setSelector(selector);
        spec.setTemplate(podTemplate);

        return rc;
    }

    public void cleanServices(String... ids) throws Exception {
        for (String id : ids) {
            try {
                boolean exists = client.services().inNamespace(configuration.getNamespace()).withName(id).cascading(false).delete();
                log.info(String.format("Service [%s] delete: %s.", id, exists));
            } catch (Exception e) {
                log.log(Level.WARNING, String.format("Exception while deleting service [%s]: %s", id, e), e);
            }
        }
    }

    public void cleanReplicationControllers(String... ids) throws Exception {
        for (String id : ids) {
            try {
                boolean exists = client.replicationControllers().inNamespace(configuration.getNamespace()).withName(id).cascading(false).delete();
                log.info(String.format("RC [%s] delete: %s.", id, exists));
            } catch (Exception e) {
                log.log(Level.WARNING, String.format("Exception while deleting RC [%s]: %s", id, e), e);
            }
        }
    }

    public void cleanPods(Map<String, String> labels) throws Exception {
        final PodList pods = client.pods().inNamespace(configuration.getNamespace()).withLabels(labels).list();
        try {
            for (Pod pod : pods.getItems()) {
                String podId = KubernetesHelper.getName(pod);
                boolean exists = client.pods().inNamespace(configuration.getNamespace()).withName(podId).cascading(false).delete();
                log.info(String.format("Pod [%s] delete: %s.", podId, exists));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception while deleting pod [%s]: %s", labels, e), e);
        }
    }

    public void close() throws IOException {
        templates.clear();
        if (client != null) {
            client.close();
        }
    }

    static IntOrString toIntOrString(ContainerPort port) {
        IntOrString intOrString = new IntOrString();
        intOrString.setIntVal(port.getContainerPort());
        return intOrString;
    }

    static Integer findHttpServicePort(List<ServicePort> ports) {
        return findServicePort(ports, "http");
    }

    static Integer findServicePort(List<ServicePort> ports, String name) {
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("Empty ports!");
        }
        if (ports.size() == 1) {
            return ports.get(0).getPort();
        }
        for (ServicePort port : ports) {
            if (name.equals(port.getName())) {
                return port.getPort();
            }
        }
        throw new IllegalArgumentException("No such port: " + name);
    }

    static IntOrString findHttpContainerPort(List<ContainerPort> ports) {
        return findContainerPort(ports, "http");
    }

    static IntOrString findContainerPort(List<ContainerPort> ports, String name) {
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("Empty ports!");
        }
        if (ports.size() == 1) {
            return toIntOrString(ports.get(0));
        }
        for (ContainerPort port : ports) {
            if (name.equals(port.getName())) {
                return toIntOrString(port);
            }
        }
        throw new IllegalArgumentException("No such port: " + name);
    }
}
