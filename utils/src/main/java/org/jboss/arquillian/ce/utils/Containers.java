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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Containers {
    private static final Logger log = Logger.getLogger(Containers.class.getName());

    public static void delayArchiveDeploy(String serverURL, long startupTimeout, long checkPeriod) throws Exception {
        delayArchiveDeploy(serverURL, startupTimeout, checkPeriod, new URLChecker() {
            public boolean check(URL stream) {
                try {
                    //noinspection EmptyTryBlock,UnusedDeclaration
                    try (InputStream is = stream.openStream()) {}
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
        });
    }

    public static void delayArchiveDeploy(String serverURL, long startupTimeout, long checkPeriod, URLChecker checker) throws Exception {
        if (serverURL == null) {
            throw new IllegalArgumentException("Null server url");
        }

        final URL server = new URL(serverURL);
        log.info(String.format("Pinging server url: %s [%ss]", serverURL, startupTimeout));

        long timeout = startupTimeout * 1000;
        while (timeout > 0) {
            Thread.sleep(checkPeriod);

            if (checker.check(server)) {
                log.info(String.format("Server [%s] is up and running.", serverURL));
                break;
            }

            timeout -= checkPeriod;
        }
        if (timeout <= 0) {
            throw new IllegalStateException(String.format("Cannot connect to server [%s], timed out.", serverURL));
        }
    }
}
