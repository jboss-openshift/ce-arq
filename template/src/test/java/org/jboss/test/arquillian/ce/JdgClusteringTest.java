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

package org.jboss.test.arquillian.ce;

import java.util.logging.Logger;

import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.test.arquillian.ce.deployment.RESTCache;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@RunWith(Arquillian.class)
@Template(url = "https://raw.githubusercontent.com/luksa/application-templates/JDG/datagrid/datagrid65-basic-s2i.json")
public class JdgClusteringTest {
    private static Logger log = Logger.getLogger(JdgClusteringTest.class.getName());
    public static final String NAMESPACE = "mluksa";
    public static final String JDG_HOST = "jdg-app-" + NAMESPACE + ".router.default.svc.cluster.local";
    public static final int JDG_PORT = 80;
    public static final String CONTEXT_PATH = "/rest";

    @Deployment
    public static WebArchive getDeployment() throws Exception {
        return ShrinkWrap.create(WebArchive.class, "test.war");
    }

    @Test
    @RunAsClient
    public void testRest() throws Exception {
        RESTCache<String, Object> cache = new RESTCache<String, Object>("default", "http://" + JDG_HOST + ":" + JDG_PORT + CONTEXT_PATH + "/");
        cache.put("foo1", "bar1");
        assertEquals("bar1", cache.get("foo1"));
    }

}
