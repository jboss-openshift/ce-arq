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

package org.jboss.arquillian.ce.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.WebHookTriggerBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.ParameterValue;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OpenShiftAdapter implements Closeable, RegistryLookup {
    private final static Logger log = Logger.getLogger(OpenShiftAdapter.class.getName());
    private static final File tmpDir;

    public static final String DEPLOYMENT_ARCHIVE_NAME_KEY = "deploymentArchiveName";

    static {
        tmpDir = getTempRoot();
    }

    private final Configuration configuration;
    private final OpenShiftClient client;
    private Map<String, File> dirs = new HashMap<>();
    private RegistryLookup lookup;
    private Map<String, KubernetesList> configs = new HashMap<>();

    protected static File getTempRoot() {
        return AccessController.doPrivileged(new PrivilegedAction<File>() {
            public File run() {
                File root = new File(System.getProperty("java.io.tmpdir"));
                log.info(String.format("Get temp root: %s", root));
                return root;
            }
        });
    }

    public OpenShiftAdapter(Configuration configuration) {
        this.configuration = configuration;

        this.client = new DefaultOpenShiftClient(configuration.getKubernetesMaster());

        if ("static".equalsIgnoreCase(configuration.getRegistryType())) {
            lookup = new StaticRegistryLookup(configuration);
        } else {
            lookup = this;
        }
    }

    public File getDir(Archive<?> archive) {
        File dir = dirs.get(archive.getName());
        if (dir == null) {
            throw new IllegalArgumentException(String.format("Missing temp dir for archive %s", archive.getName()));
        }
        return dir;
    }

    void prepare(Archive<?> archive) {
        File dir = new File(tmpDir, "ce_" + UUID.randomUUID().toString());
        if (dir.mkdirs() == false) {
            throw new IllegalStateException("Cannot create dir: " + dir);
        }
        dirs.put(archive.getName(), dir);
    }

    void reset(Archive<?> archive) {
        File dir = getDir(archive);
        delete(dir);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void delete(File target) {
        for (File file : target.listFiles()) {
            if (file.isDirectory()) {
                delete(file);
            } else {
                file.delete();
            }
        }
        target.delete();
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

    public Proxy createProxy() {
        return new Proxy(client);
    }

    public static Map<String, String> getDeploymentLabel(Archive<?> archive) {
        return Collections.singletonMap(DEPLOYMENT_ARCHIVE_NAME_KEY, archive.getName());
    }

    public File exportAsZip(File dir, Archive<?> deployment) {
        return exportAsZip(dir, deployment, deployment.getName());
    }

    public File exportAsZip(File dir, Archive<?> deployment, String name) {
        ZipExporter exporter = deployment.as(ZipExporter.class);
        File target = new File(dir, name);
        exporter.exportTo(target);
        return target;
    }

    public String buildAndPushImage(DockerFileTemplateHandler dth, InputStream dockerfileTemplate, Archive deployment, Properties properties) throws IOException {
        // Create Dockerfile
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            copy(dockerfileTemplate, baos);
        } finally {
            dockerfileTemplate.close();
        }

        properties.put("deployment.name", deployment.getName());

        // apply custom DockerFile changes
        if (dth != null) {
            dth.apply(baos);
        }

        final File dir = getDir(deployment);

        final ValueExpressionResolver resolver = new CustomValueExpressionResolver(properties);
        ValueExpression expression = new ValueExpression(baos.toString());
        String df = expression.resolveString(resolver);
        log.info(String.format("Docker file:\n---\n%s---", df));
        ByteArrayInputStream bais = new ByteArrayInputStream(df.getBytes());
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "Dockerfile"))) {
            copy(bais, fos);
        }

        // Export test deployment to Docker dir
        exportAsZip(dir, deployment);

        // Grab Docker registry service
        RegistryLookupEntry rle = lookup.lookup();

        String port = rle.getPort();
        // our Docker image name
        String imageName;
        if (port != null) {
            imageName = String.format("%s:%s/%s/%s", rle.getIp(), rle.getPort(), configuration.getNamespace(), configuration.getImageName());
        } else {
            imageName = String.format("%s/%s/%s", rle.getIp(), configuration.getNamespace(), configuration.getImageName());
        }
        log.info(String.format("Docker image name: %s", imageName));

        // Docker-java requires AuthConfig, hence this user/pass stuff
        DockerClientConfig.DockerClientConfigBuilder builder = DockerClientConfig.createDefaultConfigBuilder();
        builder.withUri(configuration.getDockerUrl());
        builder.withUsername(configuration.getUsername());
        builder.withPassword(configuration.getPassword());
        builder.withEmail(configuration.getEmail());
        builder.withServerAddress(configuration.getAddress());
        final DockerClient dockerClient = DockerClientBuilder.getInstance(builder).build();
        log.info(String.format("Docker client: %s", configuration.getDockerUrl()));

        // Build image on your Docker host
        try (BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(dir)) {
            String imageId = buildImageCmd.withTag(imageName).exec(new BuildImageResultCallback()).awaitImageId();
            log.info(String.format("Built image: %s", imageId));
        }

        // Push image to Docker registry service
        try (PushImageCmd pushImageCmd = dockerClient.pushImageCmd(imageName)) {
            String imageTag = configuration.getImageTag();
            if (imageTag != null) {
                pushImageCmd.withTag(imageTag);
            }
            pushImageCmd.exec(new PushImageResultCallback()).awaitSuccess();
            log.info(String.format("Pushed image %s with tag %s.", imageName, imageTag));
        }

        StringBuilder fullImageName = new StringBuilder(imageName);
        String imageTag = configuration.getImageTag();
        if (imageTag != null) {
            fullImageName.append(":").append(imageTag);
        }
        return fullImageName.toString();
    }

    public Project createProject(String namespace) {
        // oc new-project <namespace>
        Project project = client.projects().createNew().withNewMetadata().withName(namespace).endMetadata().done();
        // client.inNamespace(namespace).policyBindings().createNew().withNewMetadata().withName(configuration.getPolicyBinding()).endMetadata().done();

        // oc policy add-role-to-user admin admin -n <namespace>
        RoleBinding rb = new RoleBindingBuilder()
            .withNewMetadata().withName(configuration.getRoleName()).endMetadata()
            .withUserNames(configuration.getUser())
            .withGroupNames(configuration.getGroup())
            .withSubjects(new ObjectReferenceBuilder().withKind("User").withName(configuration.getUser()).build())
            .withNewRoleRef().withName(configuration.getRoleRef()).endRoleRef()
            .build();
        client.inNamespace(namespace).roleBindings().create(rb);

        return project;
    }

    public boolean deleteProject(String namespace) {
        return client.projects().withName(namespace).delete();
    }

    public KubernetesList processTemplateAndCreateResources(String name, String templateURL, String namespace, ParameterValue... values) throws Exception {
        KubernetesList list = processTemplate(templateURL, namespace, values);
        KubernetesList result = createResources(namespace, list);
        configs.put(name, result);
        return result;
    }

    private KubernetesList processTemplate(String templateURL, String namespace, ParameterValue... values) throws IOException {
        try (InputStream stream = new URL(templateURL).openStream()) {
            return client.templates().inNamespace(namespace).load(stream).process(values);
        }
    }

    private KubernetesList createResources(String namespace, KubernetesList list) {
        return client.lists().inNamespace(namespace).create(list);
    }

    public Object triggerBuild(String namespace, String buildName, String secret, String type) throws Exception {
        return client.buildConfigs().inNamespace(namespace).withName(buildName).withSecret(secret).withType(type).trigger(new WebHookTriggerBuilder().withSecret(secret).build());
    }

    public Object deleteTemplate(String name, String namespace) throws Exception {
        KubernetesList config = configs.get(name);
        if (config != null) {
            return client.lists().inNamespace(namespace).delete(config);
        }
        return null;
    }

    public String deployService(String name, Service.ApiVersion apiVersion, String portName, int port, int containerPort, Map<String, String> selector) throws Exception {
        Service service = new Service();

        service.setApiVersion(apiVersion);

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

    public Container createContainer(String image, String name, List<EnvVar> envVars, List<ContainerPort> ports, List<VolumeMount> volumes, Lifecycle lifecycle, String imagePullPolicy) throws Exception {
        Container container = new Container();
        container.setImage(image);
        container.setName(name);
        container.setEnv(envVars);
        container.setPorts(ports);
        container.setVolumeMounts(volumes);
        container.setLifecycle(lifecycle);
        container.setImagePullPolicy(imagePullPolicy);
        return container;
    }

    public PodTemplateSpec createPodTemplateSpec(Map<String, String> labels, List<Container> containers) throws Exception {
        PodTemplateSpec pts = new PodTemplateSpec();

        ObjectMeta objectMeta = new ObjectMeta();
        pts.setMetadata(objectMeta);
        objectMeta.setLabels(labels);

        PodSpec ps = new PodSpec();
        pts.setSpec(ps);
        ps.setContainers(containers);

        return pts;
    }

    public ReplicationController createReplicationController(String name, ReplicationController.ApiVersion apiVersion, Map<String, String> labels, int replicas, Map<String, String> selector, PodTemplateSpec podTemplate) throws Exception {
        ReplicationController rc = new ReplicationController();

        rc.setApiVersion(apiVersion);

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

    public String deployReplicationController(ReplicationController rc) throws Exception {
        return client.replicationControllers().inNamespace(configuration.getNamespace()).create(rc).getMetadata().getName();
    }

    public void close() throws IOException {
        configs.clear();
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

    private static void copy(InputStream input, OutputStream output) throws IOException {
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private class CustomValueExpressionResolver extends ValueExpressionResolver {
        private final Properties properties;

        public CustomValueExpressionResolver(Properties properties) {
            this.properties = properties;
        }

        @Override
        protected String resolvePart(String name) {
            String value = (String) properties.get(name);
            if (value != null) {
                return value;
            }
            return super.resolvePart(name);
        }
    }
}
