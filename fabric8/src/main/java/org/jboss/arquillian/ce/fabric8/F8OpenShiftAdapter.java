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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildList;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DoneableTemplate;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.api.model.WebHookTriggerBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.openshift.client.ParameterValue;
import io.fabric8.openshift.client.dsl.ClientTemplateResource;
import org.jboss.arquillian.ce.adapter.AbstractOpenShiftAdapter;
import org.jboss.arquillian.ce.api.MountSecret;
import org.jboss.arquillian.ce.api.model.OpenShiftResource;
import org.jboss.arquillian.ce.fabric8.model.F8DeploymentConfig;
import org.jboss.arquillian.ce.portfwd.PortForwardContext;
import org.jboss.arquillian.ce.proxy.Proxy;
import org.jboss.arquillian.ce.resources.OpenShiftResourceHandle;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.arquillian.ce.utils.HookType;
import org.jboss.arquillian.ce.utils.Operator;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.Port;
import org.jboss.arquillian.ce.utils.RCContext;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class F8OpenShiftAdapter extends AbstractOpenShiftAdapter {
    private final OpenShiftClient client;
    private Map<String, KubernetesList> templates = new ConcurrentHashMap<>();

    private static final String STORAGE = "storage";
    private static final String BOUND = "Bound";

    static OpenShiftConfig toOpenShiftConfig(Configuration configuration) {
        OpenShiftConfigBuilder builder = new OpenShiftConfigBuilder()
            .withMasterUrl(configuration.getKubernetesMaster())
            .withTrustCerts(configuration.isTrustCerts());

        if (configuration.hasOpenshiftBasicAuth()) {
            builder
                .withUsername(configuration.getOpenshiftUsername())
                .withPassword(configuration.getOpenshiftPassword());
        }

        return builder.build();
    }

    static CeOpenShiftClient create(Configuration configuration) {
        OpenShiftConfig config = toOpenShiftConfig(configuration);
        return new CeOpenShiftClient(config);
    }

    public F8OpenShiftAdapter(Configuration configuration) {
        super(configuration);
        this.client = create(configuration);
    }

    public F8OpenShiftAdapter(OpenShiftClient client, Configuration configuration) {
        super(configuration);
        this.client = client;
    }

    protected Proxy createProxy() {
        return new F8Proxy(configuration, client);
    }

    public PortForwardContext createPortForwardContext(Map<String, String> labels, int port) {
        List<Pod> pods = client.pods().inNamespace(configuration.getNamespace()).withLabels(labels).list().getItems();
        if (pods.isEmpty()) {
            throw new IllegalStateException("No such pods: " + labels);
        }
        Pod pod = pods.get(0);
        String nodeName = pod.getStatus().getHostIP();
        return new PortForwardContext(configuration.getKubernetesMaster(), nodeName, configuration.getNamespace(), pod.getMetadata().getName(), port);
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

    private Object createProject() {
        // oc new-project <namespace>
        return client.projectrequests().createNew().withNewMetadata().withName(configuration.getNamespace()).endMetadata().done();
    }

    public boolean checkProject() {
        for (Project project : client.projects().list().getItems()) {
            if (configuration.getNamespace().equals(KubernetesHelper.getName(project))) {
                return false;
            }
        }
        return createProject() != null;
    }

    public boolean deleteProject() {
        return client.projects().withName(configuration.getNamespace()).delete();
    }

    public void deletePod(String podName) {
        client.pods().inNamespace(configuration.getNamespace()).withName(podName).delete();
    }

    public String deployPod(String name, String env, RCContext context) throws Exception {
        List<Container> containers = getContainers(name, context);

        PodSpec podSpec = new PodSpec();
        podSpec.setContainers(containers);

        Map<String, String> podLabels = new HashMap<>();
        podLabels.put("name", name + "-pod");
        podLabels.putAll(context.getLabels());

        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name + "-pod");
        metadata.setLabels(podLabels);

        mountSecret(podSpec, context.getMountSecret());

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
        PodTemplateSpec podTemplate = createPodTemplateSpec(podLabels, containers, context.getMountSecret());

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

        List<VolumeMount> volumeMounts;
        MountSecret mountSecret = context.getMountSecret();
        if (mountSecret != null) {
            VolumeMount volumeMount = new VolumeMount();
            volumeMount.setName(mountSecret.volumeName());
            volumeMount.setMountPath(mountSecret.mountPath());
            volumeMounts = Collections.singletonList(volumeMount);
        } else {
            volumeMounts = Collections.emptyList();
        }

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

        Container container = createContainer(context.getImageName(), name + "-container", envVars, cps, volumeMounts, lifecycle, probe, configuration.getImagePullPolicy());

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

    public List<? extends OpenShiftResource> processTemplateAndCreateResources(String templateKey, String templateURL, List<ParamValue> values, Map<String, String> labels) throws Exception {
        List<ParameterValue> pvs = new ArrayList<>();
        for (ParamValue value : values) {
            pvs.add(new ParameterValue(value.getName(), value.getValue()));
        }
        KubernetesList list = processTemplate(templateURL, pvs, labels);
        KubernetesList result = createResources(list);
        templates.put(templateKey, result);

        List<PersistentVolumeClaim> claims = new ArrayList<>();
        List<DeploymentConfig> configs = new ArrayList<>();
        for (HasMetadata item : result.getItems()) {
            if (item instanceof PersistentVolumeClaim) {
                claims.add((PersistentVolumeClaim) item);
            } else if (item instanceof DeploymentConfig) {
                configs.add((DeploymentConfig) item);
            }
        }

        List<OpenShiftResource> retVal = new ArrayList<>();
        for (DeploymentConfig dc : configs) {
            verifyPersistentVolumes(dc, claims);
            verifyServiceAccounts(dc);
            retVal.add(new F8DeploymentConfig(dc));
        }
        return retVal;
    }

    private void verifyServiceAccounts(DeploymentConfig dc) throws Exception {
        String serviceAccountName = dc.getSpec().getTemplate().getSpec().getServiceAccountName();
        if (serviceAccountName != null) {
            ServiceAccount serviceAccount = client.serviceAccounts().inNamespace(configuration.getNamespace()).withName(serviceAccountName).get();
            if (serviceAccount == null) {
                throw new Exception("Missing required ServiceAccount: " + serviceAccountName);
            }
        }
    }

    private Volume getVolume(DeploymentConfig dc, String name) {
        List<Volume> volumes = dc.getSpec().getTemplate().getSpec().getVolumes();
        for (Volume volume : volumes) {
            if (volume.getName().equals(name)) {
                return volume;
            }
        }
        return null;
    }

    private PersistentVolumeClaim getPersistentVolumeClaim(List<PersistentVolumeClaim> claims, String claimName) {
        for (PersistentVolumeClaim pvc : claims) {
            if (pvc.getMetadata().getName().equals(claimName))
                return pvc;
        }
        return null;
    }

    private void verifyPersistentVolumes(DeploymentConfig dc, List<PersistentVolumeClaim> claims) throws Exception {
        List<Container> containers = dc.getSpec().getTemplate().getSpec().getContainers();
        for (Container container : containers) {
            List<VolumeMount> volumeMounts = container.getVolumeMounts();
            for (VolumeMount volumeMount : volumeMounts) {
                Volume volume = getVolume(dc, volumeMount.getName());
                if (volume != null && volume.getPersistentVolumeClaim() != null) {
                    String claimName = volume.getPersistentVolumeClaim().getClaimName();
                    PersistentVolumeClaim pvc = getPersistentVolumeClaim(claims, claimName);
                    if (pvc != null) {
                        if (!existsMatchingPV(pvc)) {
                            throw new Exception(String.format("Missing PersistentVolume '%s' for PersistentVolumenClaim '%s'.", volume.getName(), claimName));
                        }
                    }
                }
            }
        }
    }

    private boolean existsMatchingPV(PersistentVolumeClaim pvc) {
        String targetClaimName = pvc.getMetadata().getName();
        List<PersistentVolume> persistentVolumes = client.inAnyNamespace().persistentVolumes().list().getItems();
        for (PersistentVolume persistentVolume : persistentVolumes) {
            if (isBound(persistentVolume, targetClaimName, configuration.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBound(PersistentVolume pv, String targetClaimName, String targetNamespace) {
        ObjectReference claimRef = pv.getSpec().getClaimRef();
        if (claimRef != null && claimRef.getName().equals(targetClaimName) && claimRef.getNamespace().equals(targetNamespace)) {
            String status = pv.getStatus().getPhase();
            if (status.equals(BOUND)) {
                return true;
            }
        }
        return false;
    }

    private KubernetesList processTemplate(String templateURL, List<ParameterValue> values, Map<String, String> labels) throws IOException {
        try (InputStream stream = new URL(templateURL).openStream()) {
            ClientTemplateResource<Template, KubernetesList, DoneableTemplate> template = client.templates().inNamespace(configuration.getNamespace()).load(stream);
            template.get().getLabels().putAll(labels);
            return template.process(values.toArray(new ParameterValue[values.size()]));
        }
    }

    private KubernetesList createResources(KubernetesList list) {
        return client.lists().inNamespace(configuration.getNamespace()).create(list);
    }

    private Object triggerBuild(String namespace, String buildName, String secret, String type) throws Exception {
        return client.buildConfigs().inNamespace(namespace).withName(buildName).withSecret(secret).withType(type).trigger(new WebHookTriggerBuilder().withSecret(secret).build());
    }

    protected OpenShiftResourceHandle createResourceFromStream(InputStream stream) throws IOException {
        ModelNode json;
        try {
            json = ModelNode.fromJSONStream(stream);
        } finally {
            stream.close();
        }
        String kind = json.get("kind").asString();
        return createResourceFromJson(kind, json);
    }

    private OpenShiftResourceHandle createResourceFromJson(String kind, ModelNode json) {
        String content = json.toJSONString(true);
        if ("List".equalsIgnoreCase(kind)) {
            return new ListOpenShiftResourceHandle(content);
        } else if ("Secret".equalsIgnoreCase(kind)) {
            return new SecretOpenShiftResourceHandle(content);
        } else if ("ImageStream".equalsIgnoreCase(kind)) {
            return new ImageStreamOpenShiftResourceHandle(content);
        } else if ("ServiceAccount".equalsIgnoreCase(kind)) {
            return new ServiceAccountOpenShiftResourceHandle(content);
        } else {
            throw new IllegalArgumentException(String.format("Kind '%s' not yet supported -- use Native OpenShift adapter!", kind));
        }
    }

    public Object deleteTemplate(String templateKey) throws Exception {
        KubernetesList config = templates.get(templateKey);
        if (config != null) {
            return client.lists().inNamespace(configuration.getNamespace()).delete(config);
        }
        return config;
    }

    protected OpenShiftResourceHandle createRoleBinding(String roleRefName, String userName) {
        String subjectName = userName.substring(userName.lastIndexOf(":") + 1);
        final RoleBinding roleBinding = client
            .roleBindings()
            .inNamespace(configuration.getNamespace())
            .createNew()
            .withNewMetadata().withName(roleRefName + "-" + subjectName).endMetadata()
            .withNewRoleRef().withName(roleRefName).endRoleRef()
            .addToUserNames(userName)
            .addNewSubject().withKind("ServiceAccount").withNamespace(configuration.getNamespace()).withName(subjectName).endSubject()
            .done();
        return new OpenShiftResourceHandle() {
            public void delete() {
                client.roleBindings().inNamespace(configuration.getNamespace()).delete(roleBinding);
            }
        };
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

    public void scaleDeployment(final String name, final int replicas) throws Exception {
        ReplicationControllerList list = client.replicationControllers().inNamespace(configuration.getNamespace()).list();
        String actualName = getActualName(name, list.getItems(), "No RC found starting with " + name);
        ReplicationController rc = client.replicationControllers().inNamespace(configuration.getNamespace()).withName(actualName).scale(replicas);

        final Map<String, String> labels = rc.getSpec().getSelector();
        try {
            delay(labels, replicas, Operator.EQUAL);
        } catch (Exception e) {
            throw new DeploymentException(String.format("Timeout waiting for deployment %s to scale to %s pods", name, replicas), e);
        }
    }

    public List<String> getPods() throws Exception {
        PodList pods = client.pods().inNamespace(configuration.getNamespace()).list();
        List<String> podNames = new ArrayList<>();
        for (Pod pod : pods.getItems()) {
            podNames.add(pod.getMetadata().getName());
        }
        return podNames;
    }

    public String getLog(String name) throws Exception {
        PodList list = client.pods().inNamespace(configuration.getNamespace()).list();
        String actualName = getActualName(name, list.getItems(), "No pod found starting with " + name);
        log.info("Retrieving logs from pod " + actualName);
        return client.pods().inNamespace(configuration.getNamespace()).withName(actualName).getLog();
    }

    private String getActualName(String prefix, Iterable<? extends HasMetadata> objects, String msg) throws Exception {
        for (HasMetadata hmd : objects) {
            String name = hmd.getMetadata().getName();
            if (name.startsWith(prefix)) {
                return name;
            }
        }
        throw new Exception(msg);
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

    private PodTemplateSpec createPodTemplateSpec(Map<String, String> labels, List<Container> containers, MountSecret mountSecret) throws Exception {
        PodTemplateSpec pts = new PodTemplateSpec();

        ObjectMeta objectMeta = new ObjectMeta();
        pts.setMetadata(objectMeta);
        objectMeta.setLabels(labels);

        PodSpec ps = new PodSpec();
        pts.setSpec(ps);
        ps.setContainers(containers);

        mountSecret(ps, mountSecret);

        return pts;
    }

    private static void mountSecret(PodSpec ps, MountSecret mountSecret) {
        if (mountSecret != null) {
            Volume volume = new Volume();
            volume.setName(mountSecret.volumeName());
            volume.setSecret(new SecretVolumeSource(mountSecret.secretName()));
            ps.setVolumes(Collections.singletonList(volume));
        }
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
                boolean exists = client.pods().inNamespace(configuration.getNamespace()).withName(podId).delete();
                log.info(String.format("Pod [%s] delete: %s.", podId, exists));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception while deleting pod [%s]: %s", labels, e), e);
        }
    }

    @Override
    public void cleanRemnants(Map<String, String> labels) throws Exception {
        cleanBuilds(labels);
        cleanDeployments(labels);
    }

    private void cleanBuilds(Map<String, String> labels) throws Exception {
        final BuildList builds = client.builds().inNamespace(configuration.getNamespace()).withLabels(labels).list();
        try {
            for (Build build : builds.getItems()) {
                String buildId = KubernetesHelper.getName(build);
                boolean exists = client.builds().inNamespace(configuration.getNamespace()).withName(buildId).delete();
                log.info(String.format("Build [%s] delete: %s.", buildId, exists));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception while deleting build [%s]: %s", labels, e), e);
        }
    }

    private void cleanDeployments(Map<String, String> labels) throws Exception {
        final ReplicationControllerList rcs = client.replicationControllers().inNamespace(configuration.getNamespace()).withLabels(labels).list();
        try {
            for (ReplicationController rc : rcs.getItems()) {
                String rcId = KubernetesHelper.getName(rc);
                client.replicationControllers().inNamespace(configuration.getNamespace()).withName(rcId).scale(0, true);
                boolean exists = client.replicationControllers().inNamespace(configuration.getNamespace()).withName(rcId).delete();
                log.info(String.format("ReplicationController [%s] delete: %s.", rcId, exists));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception while deleting rc [%s]: %s", labels, e), e);
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

    private abstract class AbstractOpenShiftResourceHandle<T> implements OpenShiftResourceHandle {
        protected final T resource;

        public AbstractOpenShiftResourceHandle(String content) {
            resource = createResource(new ByteArrayInputStream(content.getBytes()));
        }

        protected abstract T createResource(InputStream stream);
    }

    private class ListOpenShiftResourceHandle extends AbstractOpenShiftResourceHandle<KubernetesList> {
        public ListOpenShiftResourceHandle(String content) {
            super(content);
        }

        protected KubernetesList createResource(InputStream stream) {
            return client.lists().inNamespace(configuration.getNamespace()).load(stream).create();
        }

        public void delete() {
            client.lists().inNamespace(configuration.getNamespace()).delete(resource);
        }
    }

    private class SecretOpenShiftResourceHandle extends AbstractOpenShiftResourceHandle<Secret> {
        public SecretOpenShiftResourceHandle(String content) {
            super(content);
        }

        protected Secret createResource(InputStream stream) {
            return client.secrets().inNamespace(configuration.getNamespace()).load(stream).create();
        }

        public void delete() {
            client.secrets().inNamespace(configuration.getNamespace()).delete(resource);
        }
    }

    private class ImageStreamOpenShiftResourceHandle extends AbstractOpenShiftResourceHandle<ImageStream> {
        public ImageStreamOpenShiftResourceHandle(String content) {
            super(content);
        }

        protected ImageStream createResource(InputStream stream) {
            return client.imageStreams().inNamespace(configuration.getNamespace()).load(stream).create();
        }

        public void delete() {
            client.imageStreams().inNamespace(configuration.getNamespace()).delete(resource);
        }
    }

    private class ServiceAccountOpenShiftResourceHandle extends AbstractOpenShiftResourceHandle<ServiceAccount> {
        public ServiceAccountOpenShiftResourceHandle(String content) {
            super(content);
        }

        protected ServiceAccount createResource(InputStream stream) {
            return client.serviceAccounts().inNamespace(configuration.getNamespace()).load(stream).create();
        }

        public void delete() {
            client.serviceAccounts().inNamespace(configuration.getNamespace()).delete(resource);
        }
    }
}
