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

package org.jboss.arquillian.ce.portfwd;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class PortForward {
    private static final Logger log = Logger.getLogger(PortForward.class.getName());
    private static final String PORT_FWD = "%s/proxy/minions/%s/portForward/%s/%s";

    private final OkHttpClient client;

    public PortForward(OkHttpClient client) {
        this.client = client;
    }

    public synchronized Closeable run(PortForwardContext context) throws Exception {
        Request.Builder builder = new Request.Builder();
        builder.url(String.format(PORT_FWD, context.getKubernetesMaster(), context.getNodeName(), context.getNamespace(), context.getPodName()));
        // https://github.com/kubernetes/kubernetes/blob/149ca1ec4971c4e5850d61d54d93b3ba315261a2/pkg/api/types.go#L1986
        builder.addHeader("port", String.valueOf(context.getPort()));
        Request request = builder.build();

        List<Interceptor> interceptors = client.networkInterceptors();
        final ConnectionInterceptor interceptor = new ConnectionInterceptor();
        interceptors.add(interceptor);
        try {
            client.newCall(request).execute();
        } finally {
            interceptors.remove(interceptor);
        }

        final ServerSocket server = new ServerSocket(context.getPort());

        Runnable runnable = new Runnable() {
            public void run() {
                while (server.isClosed() == false) {
                    try {
                        Socket socket = server.accept();
                        InputStream stream = socket.getInputStream();
                        int x;
                        while ((x = stream.read()) != -1) {
                            interceptor.getConnection().getSocket().getOutputStream().write(x);
                        }
                    } catch (IOException e) {
                        log.warning("Error: " + e.getMessage());
                    }
                }
            }
        };

        new Thread(runnable).start();

        return new Closeable() {
            public void close() throws IOException {
                doClose(server);
                doClose(interceptor.getConnection().getSocket());
            }
        };
    }

    private static void doClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static class ConnectionInterceptor implements Interceptor {
        private Connection connection;

        public Response intercept(Chain chain) throws IOException {
            connection = chain.connection();
            return chain.proceed(chain.request());
        }

        public Connection getConnection() {
            return connection;
        }
    }
}
