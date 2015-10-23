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

import static org.jboss.arquillian.ce.utils.Strings.isNotNullOrEmpty;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import com.ning.http.client.filter.RequestFilter;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import com.ning.http.util.Base64;
import net.oauth.signature.pem.PEMReader;
import net.oauth.signature.pem.PKCS1EncodedKeySpec;
import org.jboss.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class AsyncHttpClientCreator {
    static AsyncHttpClient createHttpClient(String masterURL) {
        final Config config = new Config(masterURL);

        try {
            AsyncHttpClientConfig.Builder clientConfigBuilder = new AsyncHttpClientConfig.Builder();
            clientConfigBuilder.setEnabledProtocols(config.getEnabledProtocols());

            // Follow any redirects
            clientConfigBuilder.setFollowRedirect(true);

            // Should we disable all server certificate checks?
            clientConfigBuilder.setAcceptAnyCertificate(config.isTrustCerts());

            TrustManager[] trustManagers = null;
            if (isNotNullOrEmpty(config.getCaCertFile()) || isNotNullOrEmpty(config.getCaCertData())) {
                KeyStore trustStore = createTrustStore(config.getCaCertData(), config.getCaCertFile());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            }

            KeyManager[] keyManagers = null;
            if ((isNotNullOrEmpty(config.getClientCertFile()) || isNotNullOrEmpty(config.getClientCertData())) && (isNotNullOrEmpty(config.getClientKeyFile()) || isNotNullOrEmpty(config.getClientKeyData()))) {
                KeyStore keyStore = createKeyStore(config.getClientCertData(), config.getClientCertFile(), config.getClientKeyData(), config.getClientKeyFile(), config.getClientKeyAlgo(), config.getClientKeyPassphrase().toCharArray());
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, config.getClientKeyPassphrase().toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            if (keyManagers != null || trustManagers != null) {
                if (trustManagers == null && config.isTrustCerts()) {
                    trustManagers = InsecureTrustManagerFactory.INSTANCE.getTrustManagers();
                    clientConfigBuilder.setHostnameVerifier(null);
                }
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(keyManagers, trustManagers, new SecureRandom());
                clientConfigBuilder.setSSLContext(sslContext);
            }

            if (isNotNullOrEmpty(config.getUsername()) && isNotNullOrEmpty(config.getPassword())) {
                Realm realm = new Realm.RealmBuilder()
                    .setPrincipal(config.getUsername())
                    .setPassword(config.getPassword())
                    .setUsePreemptiveAuth(true)
                    .setScheme(Realm.AuthScheme.BASIC)
                    .build();
                clientConfigBuilder.setRealm(realm);
            } else if (config.getOauthToken() != null) {
                clientConfigBuilder.addRequestFilter(new RequestFilter() {
                    @Override
                    public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
                        ctx.getRequest().getHeaders().add("Authorization", "Bearer " + config.getOauthToken());
                        return ctx;
                    }
                });
            }

            if (config.getRequestTimeout() > 0) {
                clientConfigBuilder.setRequestTimeout(config.getRequestTimeout());
            }

            if (config.getProxy() != null) {
                try {
                    URL u = new URL(config.getProxy());
                    clientConfigBuilder.setProxyServer(new ProxyServer(ProxyServer.Protocol.valueOf(u.getProtocol()), u.getHost(), u.getPort()));
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }

            NettyAsyncHttpProviderConfig nettyConfig = new NettyAsyncHttpProviderConfig();
            nettyConfig.setWebSocketMaxFrameSize(65536);
            clientConfigBuilder.setAsyncHttpClientProviderConfig(nettyConfig);

            return new AsyncHttpClient(clientConfigBuilder.build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream getInputStreamFromDataOrFile(String data, String file) throws FileNotFoundException {
        if (data != null) {
            return new ByteArrayInputStream(Base64.decode(data));
        }
        if (file != null) {
            return new FileInputStream(file);
        }
        return null;
    }

    private static KeyStore createTrustStore(String caCertData, String caCertFile) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        try (InputStream pemInputStream = getInputStreamFromDataOrFile(caCertData, caCertFile)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(pemInputStream);

            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null);

            String alias = cert.getSubjectX500Principal().getName();
            trustStore.setCertificateEntry(alias, cert);

            return trustStore;
        }
    }

    private static KeyStore createKeyStore(String clientCertData, String clientCertFile, String clientKeyData, String clientKeyFile, String clientKeyAlgo, char[] clientKeyPassphrase) throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException {
        try (InputStream certInputStream = getInputStreamFromDataOrFile(clientCertData, clientCertFile)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);

            InputStream keyInputStream = getInputStreamFromDataOrFile(clientKeyData, clientKeyFile);
            PEMReader reader = new PEMReader(keyInputStream);

            PrivateKey privateKey;

            KeyFactory keyFactory = KeyFactory.getInstance(clientKeyAlgo);
            try {
                // First let's try PKCS8
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(reader.getDerBytes()));
            } catch (InvalidKeySpecException e) {
                // Otherwise try PKCS8
                RSAPrivateCrtKeySpec keySpec = new PKCS1EncodedKeySpec(reader.getDerBytes()).getKeySpec();
                privateKey = keyFactory.generatePrivate(keySpec);
            }

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, clientKeyPassphrase);

            String alias = cert.getSubjectX500Principal().getName();
            keyStore.setKeyEntry(alias, privateKey, clientKeyPassphrase, new Certificate[]{cert});

            return keyStore;
        }
    }

}
