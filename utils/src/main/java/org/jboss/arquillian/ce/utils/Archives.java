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

import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Archives {
    public static Archive<?> toProxy(final Archive<?> archive, final String newArchiveName) {
        Class<? extends Archive> expected = (archive instanceof EnterpriseArchive) ? EnterpriseArchive.class : WebArchive.class;
        return BytecodeUtils.proxy(expected, new MethodHandler() {
            public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {
                if ("getName".equals(method.getName())) {
                    return newArchiveName;
                } else {
                    return method.invoke(archive, args);
                }
            }
        });
    }
}
