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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.ce.api.TemplateParameter;
import org.jboss.arquillian.ce.runinpod.RunInPodContainer;
import org.jboss.arquillian.ce.runinpod.RunInPodContext;
import org.jboss.arquillian.ce.utils.AbstractCEContainer;
import org.jboss.arquillian.ce.utils.Archives;
import org.jboss.arquillian.ce.utils.ParamValue;
import org.jboss.arquillian.ce.utils.StringResolver;
import org.jboss.arquillian.ce.utils.Strings;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TemplateCEContainer extends AbstractCEContainer<TemplateCEConfiguration> {
    @Override
    protected RunInPodContainer create() {
        RunInPodContext context = new RunInPodContext(configuration, parallelHandler);
        return runInPodUtils.createContainer(context);
    }

    public Class<TemplateCEConfiguration> getConfigurationClass() {
        return TemplateCEConfiguration.class;
    }

    public void apply(OutputStream outputStream) throws IOException {
    }

    @SuppressWarnings("unchecked")
    private void addParameterValues(List<ParamValue> values, Map map, boolean filter) {
        Set<Map.Entry> entries = map.entrySet();
        for (Map.Entry env : entries) {
            if (env.getKey() instanceof String && env.getValue() instanceof String) {
                String key = (String) env.getKey();
                if (filter == false || key.startsWith("ARQ_") || key.startsWith("arq_")) {
                    if (filter) {
                        values.add(new ParamValue(key.substring("ARQ_".length()), (String) env.getValue()));
                    } else {
                        values.add(new ParamValue(key, (String) env.getValue()));
                    }
                }
            }
        }
    }

    public ProtocolMetaData doDeploy(final Archive<?> archive) throws DeploymentException {
        final StringResolver resolver = Strings.createStringResolver(configuration.getProperties());
        final String templateURL = readTemplateUrl(resolver);
        try {
            final String newArchiveName;
            boolean externalDeployment = Archives.isExternalDeployment(tc.get().getJavaClass());
            if (externalDeployment) {
                log.info("Ignoring Arquillian deployment ...");
                newArchiveName = newName(archive);
            } else {
                log.info(String.format("Using Git repository: %s, committing Arquillian deployment ...", configuration.getGitRepository(true)));
                newArchiveName = commitDeployment(archive);
            }

            Archive<?> proxy = Archives.toProxy(archive, newArchiveName);

            int replicas = readReplicas();
            Map<String, String> labels = readLabels(resolver);
            if (labels.isEmpty()) {
                log.warning(String.format("Empty labels for template: %s, namespace: %s", templateURL, configuration.getNamespace()));
            }

            if (executeProcessTemplate()) {
                List<ParamValue> values = new ArrayList<>();
                addParameterValues(values, System.getenv(), true);
                addParameterValues(values, System.getProperties(), true);
                addParameterValues(values, readParameters(resolver), false);
                values.add(new ParamValue("REPLICAS", String.valueOf(replicas))); // not yet supported
                if (externalDeployment == false || (configuration.getGitRepository(false) != null)) {
                    values.add(new ParamValue("SOURCE_REPOSITORY_URL", resolver.resolve(configuration.getGitRepository(true))));
                }

                log.info(String.format("Applying OpenShift template: %s", templateURL));
                // use old archive name as templateKey
                client.processTemplateAndCreateResources(archive.getName(), templateURL, values);
            } else {
                log.info(String.format("Ignoring template [%s] processing ...", templateURL));
            }

            return getProtocolMetaData(proxy, labels, replicas);
        } catch (Throwable t) {
            throw new DeploymentException("Cannot deploy template: " + templateURL, t);
        }
    }

    protected Template readTemplate() {
        return tc.get().getAnnotation(Template.class);
    }

    protected String readTemplateUrl(StringResolver resolver) {
        Template template = readTemplate();
        String templateUrl = template == null ? null : template.url();
        if (templateUrl == null || templateUrl.length() == 0) {
            templateUrl = resolver.resolve(configuration.getTemplateURL());
        }

        if (templateUrl == null) {
            throw new IllegalArgumentException("Missing template URL! Either add @Template to your test or add -Dopenshift.template.url=<url>");
        }

        return templateUrl;
    }

    private Map<String, String> readLabels(StringResolver resolver) {
        Template template = readTemplate();
        if (template != null) {
            String string = template.labels();
            if (string != null && string.length() > 0) {
                Map<String, String> map = Strings.splitKeyValueList(string);
                Map<String, String> resolved = new HashMap<>();
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    resolved.put(resolver.resolve(entry.getKey()), resolver.resolve(entry.getValue()));
                }
                return resolved;
            }
        }
        return configuration.getTemplateLabels();
    }

    private boolean executeProcessTemplate() {
        Template template = readTemplate();
        return (template == null || template.process()) && configuration.isTemplateProcess();
    }

    private Map<String, String> readParameters(StringResolver resolver) {
        Template template = readTemplate();
        if (template != null) {
            TemplateParameter[] parameters = template.parameters();
            Map<String, String> map = new HashMap<>();
            for (TemplateParameter parameter : parameters) {
                String name = resolver.resolve(parameter.name());
                String value = resolver.resolve(parameter.value());
                map.put(name, value);
            }
            return map;
        }
        return configuration.getTemplateParameters();
    }

    @Override
    protected void cleanup(Archive<?> archive) throws Exception {
        client.deleteTemplate(archive.getName());
    }

    protected String newName(Archive<?> archive) {
        if (archive instanceof WebArchive == false) {
            throw new IllegalArgumentException("Cannot deploy non .war deployments!");
        }

        return archive.getName();
//        return "ROOT.war"; // TODO -- handle .ear?
    }

    protected String commitDeployment(Archive<?> archive) throws Exception {
        String name = newName(archive);

        File dir = dockerAdapter.getDir(archive);
        try (GitAdapter git = GitAdapter.cloneRepository(dir, configuration.getGitRepository(true)).prepare("deployments/" + name)) {
            dockerAdapter.exportAsZip(new File(dir, "deployments"), archive, name);

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

        return name;
    }

    protected String getPrefix() {
        return "tmpl";
    }
}
