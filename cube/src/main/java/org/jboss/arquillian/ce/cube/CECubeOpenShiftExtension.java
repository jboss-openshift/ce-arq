/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other
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
package org.jboss.arquillian.ce.cube;

import org.jboss.arquillian.ce.cube.enrichers.RouteURLEnricher;
import org.jboss.arquillian.ce.ext.ExternalDeploymentScenarioGenerator;
import org.jboss.arquillian.ce.ext.LocalConfigurationResourceProvider;
import org.jboss.arquillian.ce.ext.OpenShiftHandleResourceProvider;
import org.jboss.arquillian.ce.ext.UtilsArchiveAppender;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentScenarioGenerator;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

public class CECubeOpenShiftExtension implements LoadableExtension {
    public void register(ExtensionBuilder builder) {
        builder.observer(CECubeInitializer.class)
               .observer(CEEnvironmentProcessor.class);

        builder.service(TestEnricher.class, RouteURLEnricher.class);
        builder.service(ResourceProvider.class, OpenShiftHandleResourceProvider.class);
        builder.service(ResourceProvider.class, LocalConfigurationResourceProvider.class);
        builder.service(AuxiliaryArchiveAppender.class, UtilsArchiveAppender.class);
        builder.service(DeploymentScenarioGenerator.class, ExternalDeploymentScenarioGenerator.class);
    }
}
