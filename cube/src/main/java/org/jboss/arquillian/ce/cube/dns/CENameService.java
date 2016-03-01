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
package org.jboss.arquillian.ce.cube.dns;

import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import sun.net.spi.nameservice.NameService;

/**
 * CENameService
 * 
 * @author Rob Cernich
 */
public class CENameService implements NameService {

    private static Set<String> hosts = new HashSet<String>();
    private static InetAddress[] routerAddr = new InetAddress[1];

    public static void setRoutes(RouteList routeList, String routerHost) {
        synchronized (hosts) {
            hosts.clear();
            try {
                CENameService.routerAddr[0] = routerHost == null ? null : InetAddress.getByName(routerHost);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP for router host", e);
            }
            if (routeList == null) {
                return;
            }
            for (Route route : routeList.getItems()) {
                final String routeHostname = route.getSpec().getHost();
                System.out.println(String.format("Adding route to name service: %s %s", routerHost, routeHostname));
                hosts.add(routeHostname);
            }
        }
    }

    @Override
    public InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException {
        synchronized (hosts) {
            if (routerAddr[0] != null && hosts.contains(host)) {
                return routerAddr;
            }
            throw new UnknownHostException(host);
        }
    }

    @Override
    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        throw new UnknownHostException();
    }

}
