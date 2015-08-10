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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.ObjectMeta;
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
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class K8sClient implements Closeable {
    private final static Logger log = Logger.getLogger(K8sClient.class.getName());
    private static final File tmpDir;

    static {
        tmpDir = getTempRoot();
    }

    private final Configuration configuration;
    private final KubernetesClient client;
    private File dir;

    protected static File getTempRoot() {
        return AccessController.doPrivileged(new PrivilegedAction<File>() {
            public File run() {
                File root = new File(System.getProperty("java.io.tmpdir"));
                log.info(String.format("Get temp root: %s", root));
                return root;
            }
        });
    }

    public K8sClient(Configuration configuration) {
        this.configuration = configuration;

        Config config = new ConfigBuilder().withMasterUrl(configuration.getKubernetesMaster()).build();

        this.client = new DefaultKubernetesClient(config);

        this.dir = new File(tmpDir, "ce_" + UUID.randomUUID().toString());
        if (this.dir.mkdirs() == false) {
            throw new IllegalStateException("Cannot create dir: " + dir);
        }
    }

    public String buildAndPushImage(InputStream dockerfileTemplate, Archive deployment, Properties properties) throws IOException {
        // Create Dockerfile
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            copy(dockerfileTemplate, baos);
        } finally {
            dockerfileTemplate.close();
        }

        properties.put("deployment.name", deployment.getName());

        final ValueExpressionResolver resolver = new CustomValueExpressionResolver(properties);

        ValueExpression expression = new ValueExpression(baos.toString());
        String df = expression.resolveString(resolver);
        ByteArrayInputStream bais = new ByteArrayInputStream(df.getBytes());
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "Dockerfile"))) {
            copy(bais, fos);
        }

        // Export test deployment to Docker dir
        ZipExporter exporter = deployment.as(ZipExporter.class);
        exporter.exportTo(new File(dir, deployment.getName()));

        // Grab Docker registry service
        Service service = getService(configuration.getRegistryNamespace(), configuration.getRegistryServiceName());
        ServiceSpec spec = service.getSpec();
        String ip = spec.getClusterIP();
        Integer port = findHttpServicePort(spec.getPorts());

        // our Docker image name
        final String imageName = String.format("%s:%s/%s/%s", ip, port, configuration.getProject(), configuration.getImageName());
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
        String imageId;
        try (BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(dir)) {
            buildImageCmd.withTag(imageName);
            BuildImageCmd.Response response = buildImageCmd.exec();
            String output = Strings.toString(response);
            imageId = Strings.substringBetween(output, "Successfully built ", "\\n\"}");
            if (imageId == null) {
                throw new IOException(String.format("Error building image: %s", output));
            }
            log.info(String.format("Built image: %s", imageId));
        }

        // Push image to Docker registry service
        try (PushImageCmd pushImageCmd = dockerClient.pushImageCmd(imageName)) {
            PushImageCmd.Response response = pushImageCmd.exec();
            String pushInfo = Strings.toString(response);
            log.info(String.format("Push image [%s] info: %s", imageName, pushInfo));
        }

        return imageName;
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

    public Container createContainer(String image, String name, List<EnvVar> envVars, List<ContainerPort> ports, List<VolumeMount> volumes, Lifecycle lifecycle) throws Exception {
        Container container = new Container();
        container.setImage(image);
        container.setName(name);
        container.setEnv(envVars);
        container.setPorts(ports);
        container.setVolumeMounts(volumes);
        container.setLifecycle(lifecycle);
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
                boolean exists = client.services().inNamespace(configuration.getNamespace()).withName(id).delete();
                log.info(String.format("Service [%s] delete: %s.", id, exists));
            } catch (Exception ignored) {
            }
        }
    }

    public void cleanReplicationControllers(String... ids) throws Exception {
        for (String id : ids) {
            try {
                boolean exists = client.replicationControllers().inNamespace(configuration.getNamespace()).withName(id).delete();
                log.info(String.format("RC [%s] delete: %s.", id, exists));
            } catch (Exception ignored) {
            }
        }
    }

    public void cleanPods(String... names) throws Exception {
        final PodList pods = client.pods().inNamespace(configuration.getNamespace()).list();
        for (String name : names) {
            try {
                for (Pod pod : pods.getItems()) {
                    String podId = KubernetesHelper.getName(pod);
                    if (podId.startsWith(name)) {
                        boolean exists = client.pods().inNamespace(configuration.getNamespace()).withName(podId).delete();
                        log.info(String.format("Pod [%s] delete: %s.", podId, exists));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public String deployReplicationController(ReplicationController rc) throws Exception {
        return client.replicationControllers().inNamespace(configuration.getNamespace()).create(rc).getMetadata().getName();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void close() throws IOException {
        for (File file : dir.listFiles()) {
            file.delete();
        }
        dir.delete();
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
