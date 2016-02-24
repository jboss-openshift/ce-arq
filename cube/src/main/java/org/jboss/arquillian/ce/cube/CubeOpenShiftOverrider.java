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

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ExtensionDef;
import org.jboss.arquillian.core.api.annotation.Observes;

/**
 * CubeOpenShiftOverrider
 * <p/>
 * Processes overrides for Arquillian Cube OpenShift extension.
 * 
 * @author Rob Cernich
 */
public class CubeOpenShiftOverrider {

    /**
     * Override values used to initialize CubeOpenShiftConfiguration so we're
     * all on the same page.
     * 
     * @param config
     * @param arquillianDescriptor
     */
    public void overrideCubeOpenShiftConfiguration(@Observes CECubeConfiguration config,
            ArquillianDescriptor arquillianDescriptor) {
        final ExtensionDef cubeOpenShiftExtension = arquillianDescriptor.extension("openshift");
        cubeOpenShiftExtension.property("originServer", config.getKubernetesMaster());
        cubeOpenShiftExtension.property("namespace", config.getNamespace());
        // might look at setting definitions here, although i'm not sure how
        // this will work if we need to do template processing
        // cubeOpenShiftExtension.property("definitions", getDefinitions());
    }

}
