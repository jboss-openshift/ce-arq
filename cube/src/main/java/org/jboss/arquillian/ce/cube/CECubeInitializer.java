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

import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.jboss.arquillian.ce.adapter.OpenShiftAdapter;
import org.jboss.arquillian.ce.api.ConfigurationHandle;
import org.jboss.arquillian.ce.fabric8.F8OpenShiftAdapter;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;

/**
 * CECubeInitializer
 * <p/>
 * Initializer for CE Arquillian Cube extension.
 * 
 * @author Rob Cernich
 */
public class CECubeInitializer {

    @Inject
    @ApplicationScoped
    private InstanceProducer<ConfigurationHandle> configurationHandleProducer;

    @Inject
    @ApplicationScoped
    private InstanceProducer<CECubeConfiguration> configurationProducer;

    @Inject
    @ApplicationScoped
    private InstanceProducer<OpenShiftAdapter> openShiftAdapterProducer;

    public void configure(@Observes ArquillianDescriptor arquillianDescriptor) {
        // read in our configuration
        CECubeConfiguration config = CECubeConfiguration.fromMap(arquillianDescriptor.extension("ce-cube")
                .getExtensionProperties());
        configurationProducer.set(config);
        configurationHandleProducer.set(config);
    }
    
    public void createOpenShiftAdapter(@Observes OpenShiftClient client, CECubeConfiguration configuration) {
        openShiftAdapterProducer.set(new F8OpenShiftAdapter(client.getClientExt(), configuration));
    }
}
