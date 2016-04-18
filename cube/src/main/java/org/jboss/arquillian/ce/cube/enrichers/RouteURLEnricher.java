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
package org.jboss.arquillian.ce.cube.enrichers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import io.fabric8.openshift.api.model.Route;
import org.arquillian.cube.impl.util.ReflectionUtil;
import org.arquillian.cube.openshift.impl.client.OpenShiftClient;
import org.jboss.arquillian.ce.cube.CECubeConfiguration;
import org.jboss.arquillian.ce.cube.RouteURL;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestEnricher;

/**
 * RouteProxyProvider
 * 
 * @author Rob Cernich
 */
public class RouteURLEnricher implements TestEnricher {

    @Inject
    private Instance<OpenShiftClient> clientInstance;

    @Inject
    private Instance<CECubeConfiguration> configurationInstance;

    @Override
    public void enrich(Object testCase) {
        for (Field field : ReflectionUtil.getFieldsWithAnnotation(testCase.getClass(), RouteURL.class)) {
            try {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                field.set(testCase, lookup(getRouteURLAnnotation(field.getAnnotations())));
            } catch (Exception e) {
                throw new RuntimeException("Could not set RouteURL value on field " + field);
            }
        }
    }

    @Override
    public Object[] resolve(Method method) {
        Object[] values = new Object[method.getParameterTypes().length];
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            RouteURL routeURL = getRouteURLAnnotation(method.getParameterAnnotations()[i]);
            if (routeURL != null) {
                values[i] = lookup(routeURL);
            }
        }
        return values;
    }

    private RouteURL getRouteURLAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == RouteURL.class) {
                return (RouteURL) annotation;
            }
        }
        return null;
    }

    private URL lookup(RouteURL routeURL) {
        if (routeURL == null) {
            throw new NullPointerException("RouteURL is null!");
        }
        final String routeName = routeURL.value();
        if (routeName == null || routeName.length() == 0) {
            throw new IllegalArgumentException("Must specify a route name!");
        }
        final CECubeConfiguration config = configurationInstance.get();
        final String host = config.getRouterHost();
        if (host == null || host.length() == 0) {
            throw new IllegalArgumentException("Must specify routerHost!");
        }
        final OpenShiftClient client = clientInstance.get();
        final Route route = client.getClientExt().routes().inNamespace(config.getNamespace()).withName(routeName).get();
        if (route == null) {
            throw new IllegalArgumentException("Could not resolve route: " + routeName);
        }
        final String routeHostname = route.getSpec().getHost();
        final String protocol;
        final int port;
        if (route.getSpec().getTls() == null) {
            protocol = "http";
            port = config.getRouterHttpPort();
        } else {
            protocol = "https";
            port = config.getRouterHttpsPort();
        }
        try {
            return new URL(protocol, routeHostname, port, "/");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unable to create route URL", e);
        }
    }

}
