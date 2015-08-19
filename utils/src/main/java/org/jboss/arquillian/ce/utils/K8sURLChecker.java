package org.jboss.arquillian.ce.utils;

import java.net.URL;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.com.ning.http.client.AsyncHttpClient;
import io.fabric8.kubernetes.client.internal.com.ning.http.client.Response;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class K8sURLChecker implements URLChecker {
    private KubernetesClient client;

    public K8sURLChecker(KubernetesClient client) {
        this.client = client;
    }

    public boolean check(URL url) {
        AsyncHttpClient httpClient = client.getHttpClient();
        AsyncHttpClient.BoundRequestBuilder builder = httpClient.preparePost(url.toExternalForm());
        try {
            Response response = builder.execute().get();
            response.getStatusCode();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
