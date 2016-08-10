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

package org.jboss.arquillian.ce.openshift;

import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IPod;
import okhttp3.OkHttpClient;
import org.jboss.arquillian.ce.proxy.AbstractProxy;
import org.jboss.arquillian.ce.utils.Configuration;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class NativeProxy extends AbstractProxy<IPod> {
    private final IClient client;
    private final HttpClientCreator.CeOkHttpClient httpClient;

    public NativeProxy(Configuration configuration, IClient client) {
        super(configuration);
        this.client = client;
        this.httpClient = HttpClientCreator.createHttpClient(configuration);
    }

    public SSLContext getSSLContext() {
        return httpClient.getSslContext();
    }

    protected OkHttpClient getHttpClient() {
        return httpClient;
    }

    protected List<IPod> getPods(Map<String, String> labels) {
        return client.list(ResourceKind.POD, configuration.getNamespace(), labels);
    }

    protected String getName(IPod pod) {
        return pod.getName();
    }

    protected boolean isReady(IPod pod) {
        ModelNode root = ModelNode.fromJSONString(pod.toJson());
        ModelNode statusNode = root.get("status");
        ModelNode phaseNode = statusNode.get("phase");
        if (!phaseNode.isDefined() || !"Running".equalsIgnoreCase(phaseNode.asString())) {
            return false;
        }
        ModelNode conditionsNode = statusNode.get("conditions");
        if (!conditionsNode.isDefined()) {
            return false;
        }
        List<ModelNode> conditions = conditionsNode.asList();
        for (ModelNode condition : conditions) {
            ModelNode conditionTypeNode = condition.get("type");
            ModelNode conditionStatusNode = condition.get("status");
            if (conditionTypeNode.isDefined() && "Ready".equalsIgnoreCase(conditionTypeNode.asString()) &&
                conditionStatusNode.isDefined() && "True".equalsIgnoreCase(conditionStatusNode.asString())) {
                return true;
            }
        }
        return false;
    }
}
