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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractOpenShiftAdapter implements OpenShiftAdapter {
    private final static Logger log = Logger.getLogger(AbstractOpenShiftAdapter.class.getName());
    private static final File tmpDir;

    static {
        tmpDir = getTempRoot();
    }

    protected final Configuration configuration;

    private Map<String, File> dirs = new HashMap<>();
    private RegistryLookup lookup;

    protected static File getTempRoot() {
        return AccessController.doPrivileged(new PrivilegedAction<File>() {
            public File run() {
                File root = new File(System.getProperty("java.io.tmpdir"));
                log.info(String.format("Get temp root: %s", root));
                return root;
            }
        });
    }

    protected AbstractOpenShiftAdapter(Configuration configuration) {
        this.configuration = configuration;

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

    public void prepare(Archive<?> archive) {
        File dir = new File(tmpDir, "ce_" + UUID.randomUUID().toString());
        if (dir.mkdirs() == false) {
            throw new IllegalStateException("Cannot create dir: " + dir);
        }
        dirs.put(archive.getName(), dir);
    }

    public void reset(Archive<?> archive) {
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

        String dockerUrl = configuration.getDockerUrl();
        if (dockerUrl == null) {
            throw new IllegalArgumentException("Missing Docker url / host!");
        }

        // Docker-java requires AuthConfig, hence this user/pass stuff
        DockerClientConfig.DockerClientConfigBuilder builder = DockerClientConfig.createDefaultConfigBuilder();
        builder.withUri(dockerUrl);
        builder.withUsername(configuration.getUsername());
        builder.withPassword(configuration.getPassword());
        builder.withEmail(configuration.getEmail());
        builder.withServerAddress(configuration.getAddress());
        final DockerClient dockerClient = DockerClientBuilder.getInstance(builder).build();
        log.info(String.format("Docker client: %s", dockerUrl));

        final Timer timer = new Timer();

        // Build image on your Docker host
        try (BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(dir)) {
            timer.reset();
            String imageId = buildImageCmd.withTag(imageName).exec(new PrintBuildImageResultCallback()).awaitImageId();
            log.info(String.format("Built image: %s [%s].", imageId, timer));
        }

        final String imageTag = configuration.getImageTag();

        // Push image to Docker registry service
        log.info(String.format("Pushing image %s with tag %s ...", imageName, imageTag));
        try (PushImageCmd pushImageCmd = dockerClient.pushImageCmd(imageName)) {
            if (imageTag != null) {
                pushImageCmd.withTag(imageTag);
            }
            timer.reset();
            pushImageCmd.exec(new PrintPushImageResultCallback()).awaitSuccess();
            log.info(String.format("Pushed image %s with tag %s [%s].", imageName, imageTag, timer));
        }

        StringBuilder fullImageName = new StringBuilder(imageName);
        if (imageTag != null) {
            fullImageName.append(":").append(imageTag);
        }
        return fullImageName.toString();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static void printResponse(String prefix, String result) {
        if (result != null) {
            log.info(String.format("%s: %s", prefix, result));
        }
    }

    private static class PrintBuildImageResultCallback extends BuildImageResultCallback {
        @Override
        public void onNext(BuildResponseItem item) {
            super.onNext(item);
            printResponse("Build stream", item.getStream());
        }
    }

    private static class PrintPushImageResultCallback extends PushImageResultCallback {
        @Override
        public void onNext(PushResponseItem item) {
            super.onNext(item);
            printResponse(String.format("Push progress [%s]", item.getId()), item.getProgress());
        }
    }
}
