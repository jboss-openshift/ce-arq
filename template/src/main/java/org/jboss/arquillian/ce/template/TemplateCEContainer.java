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

package org.jboss.arquillian.ce.template;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.openshift.client.ParameterValue;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jboss.arquillian.ce.protocol.CEServletProtocol;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.ce.utils.OpenShiftAdapter;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TemplateCEContainer extends AbstractCEContainer<TemplateCEConfiguration> {
    public Class<TemplateCEConfiguration> getConfigurationClass() {
        return TemplateCEConfiguration.class;
    }

    public void apply(OutputStream outputStream) throws IOException {
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(CEServletProtocol.PROTOCOL_NAME);
    }

    @SuppressWarnings("unchecked")
    private void addParameterValues(List<ParameterValue> values, Map map) {
        Set<Map.Entry> entries = map.entrySet();
        for (Map.Entry env : entries) {
            if (env.getKey() instanceof String && env.getValue() instanceof String) {
                String key = (String) env.getKey();
                if (key.startsWith("ARQ_")) {
                    values.add(new ParameterValue(key.substring("ARQ_".length()), (String) env.getValue()));
                }
            }
        }
    }

    public ProtocolMetaData doDeploy(Archive<?> archive) throws DeploymentException {
        String templateURL = readTemplateUrl();
        try {
            log.info(String.format("Using Git repository: %s", configuration.getGitRepository()));
            commitDeployment(archive);

            int replicas = readReplicas();
            Map<String, String> labels = OpenShiftAdapter.getDeploymentLabel(archive);

            List<ParameterValue> values = new ArrayList<>();
            addParameterValues(values, System.getenv());
            addParameterValues(values, System.getProperties());
            values.add(new ParameterValue("SOURCE_REPOSITORY_URL", configuration.getGitRepository()));
            values.add(new ParameterValue("REPLICAS", String.valueOf(replicas))); // not yet supported
            values.add(new ParameterValue("DEPLOYMENT_NAME", labels.get(OpenShiftAdapter.DEPLOYMENT_ARCHIVE_NAME_KEY)));

            log.info(String.format("Applying OpenShift template: %s", templateURL));
            client.processTemplateAndCreateResources(archive.getName(), templateURL, configuration.getNamespace(), values.toArray(new ParameterValue[values.size()]));
//            log.info(String.format("Triggering build: %s", configuration.getBuildName()));
//            client.triggerBuild(configuration.getNamespace(), configuration.getBuildName(), configuration.getBuildSecret(), configuration.getBuildType());

            return getProtocolMetaData(archive, replicas);
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy template: " + templateURL, t);
        }
    }

    protected String readTemplateUrl() {
        TestClass testClass = tc.get();
        Template template = testClass.getAnnotation(Template.class);
        String templateUrl = template == null ? null : template.url();
        if (templateUrl == null) {
            templateUrl = configuration.getTemplateURL();
        }

        if (templateUrl == null) {
            throw new IllegalArgumentException("Missing template URL! Either add @Template to your test or add -Dopenshift.template.url=<url>");
        }
        return templateUrl;
    }

    @Override
    protected void cleanup(Archive<?> archive) throws Exception {
        client.deleteTemplate(archive.getName(), configuration.getNamespace());
    }

    protected void commitDeployment(Archive<?> archive) throws Exception {
        if (archive instanceof WebArchive == false) {
            throw new IllegalArgumentException("Cannot deploy non .war deployments!");
        }

        String name = "ROOT.war"; // TODO -- handle .ear?

        File dir = client.getDir(archive);
        try (GitAdapter git = GitAdapter.cloneRepository(dir, configuration.getGitRepository()).prepare("deployments/" + name)) {
            client.exportAsZip(new File(dir, "deployments"), archive, name);

            CredentialsProvider cp;
            if (configuration.isNetRC()) {
                cp = new NetRCCredentialsProvider();
            } else {
                cp = new UsernamePasswordCredentialsProvider(configuration.getGitUsername(), configuration.getGitPassword());
            }

            git
                .setCredentials(cp)
                .add("deployments")
                .commit()
                .push();
        }
    }

    protected String getPrefix() {
        return "tmpl";
    }
}
