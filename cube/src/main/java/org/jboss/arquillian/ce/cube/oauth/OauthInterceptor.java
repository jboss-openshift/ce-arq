package org.jboss.arquillian.ce.cube.oauth;

import io.fabric8.utils.Base64Encoder;
import org.jboss.arquillian.ce.httpclient.HttpClient;
import org.jboss.arquillian.ce.httpclient.HttpClientBuilder;
import org.jboss.arquillian.ce.httpclient.HttpRequest;
import org.jboss.arquillian.ce.httpclient.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Created by fspolti on 6/20/16.
 */
public class OauthInterceptor {

    private final Logger log = Logger.getLogger(OauthInterceptor.class.getName());

    private final String TOKEN_REQUEST_URI = "oauth/token/request?client_id=openshift-challenging-client";
    private String OPENSHIFT_URL = "https://localhost:8443";
    private String USERNAME = "guest";
    private String PASSWORD = "guest";

    public String getToken(String openshiftUrl, String uid, String pwd) throws Exception {

        OPENSHIFT_URL = openshiftUrl != null ? openshiftUrl : OPENSHIFT_URL;
        USERNAME = uid != null ? uid : USERNAME;
        PASSWORD = pwd != null ? pwd : PASSWORD;

        log.info("Issuing a new token for user " + USERNAME);

        HttpRequest request = HttpClientBuilder.doGET(OPENSHIFT_URL + "/" +TOKEN_REQUEST_URI);
        request.setHeader("Authorization", "Basic " + authEncoding());

        HttpClient client = getClient();
        HttpResponse response = client.execute(request);

        StringBuilder result = getResult(new BufferedReader(new InputStreamReader((response.getEntity().getContent()))));

        log.info("Request result: " + result);
        String token = result.substring(result.indexOf("<code>") + 6, result.indexOf("</code>"));
        log.info("Token: " + token);

        return token;
    }

    private StringBuilder getResult(BufferedReader br) throws IOException {
        StringBuilder result = new StringBuilder();
        String line = "";
        while ((line = br.readLine()) != null) {
            result.append(line);
        }
        br.close();
        return result;
    }

    private String authEncoding() {
        return Base64Encoder.encode(USERNAME + ":" + PASSWORD);
    }

    private HttpClient getClient() throws Exception {
        return HttpClientBuilder.untrustedConnectionClient();
    }
}