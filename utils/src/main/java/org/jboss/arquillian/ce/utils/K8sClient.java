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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.EventStreamItem;
import com.github.dockerjava.api.model.PushEventStreamItem;
import com.github.dockerjava.core.DockerClientBuilder;
import io.fabric8.kubernetes.api.KubernetesClient;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
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
        this.client = new KubernetesClient(configuration.getKubernetesMaster());
        this.dir = new File(tmpDir, "ce_" + UUID.randomUUID().toString());
    }

    public String pushImage(InputStream dockerfileTemplate, Archive deployment, Map<Object, Object> properties) throws IOException {
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

        ZipExporter exporter = deployment.as(ZipExporter.class);
        exporter.exportTo(new File(dir, deployment.getName()));

        DockerClient dockerClient = DockerClientBuilder.getInstance(configuration.getDockerHost()).build();

        String imageTag;
        try (BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(dir)) {
            BuildImageCmd.Response response = buildImageCmd.exec();
            Iterable<EventStreamItem> items = response.getItems();
            EventStreamItem item = items.iterator().next();
            imageTag = item.getStream();
        }

        String dockerServiceId = (String) properties.get("docker.service.id");
        if (dockerServiceId == null) {
            dockerServiceId = "docker-registry";
        }
        Service service = client.getService(dockerServiceId);
        String ip = service.getPortalIP();
        Integer port = service.getPort();

        final String imageName = String.format("%s:%s/%s", ip, port, imageTag);
        try (PushImageCmd pushImageCmd = dockerClient.pushImageCmd(imageName)) {
            PushImageCmd.Response response = pushImageCmd.exec();
            Iterable<PushEventStreamItem> items = response.getItems();
            PushEventStreamItem item = items.iterator().next();
            log.info(String.format("Push image [%s] status: %s", imageName, item.getStatus()));
        }
        return imageName;
    }

    public String deployService(String id, String apiVersion, int port, int containerPort, Map<String, String> selector) throws Exception {
        Service service = new Service();
        service.setId(id);
        service.setApiVersion(apiVersion);
        service.setPort(port);
        IntOrString cp = new IntOrString();
        cp.setIntVal(containerPort);
        service.setContainerPort(cp);
        service.setSelector(selector);
        return client.createService(service);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void close() throws IOException {
        for (File file : dir.listFiles()) {
            file.delete();
        }
        dir.delete();
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
        private final Map<Object, Object> properties;

        public CustomValueExpressionResolver(Map<Object, Object> properties) {
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
