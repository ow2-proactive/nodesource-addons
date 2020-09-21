/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.resourcemanager.nodesource.infrastructure;

import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.ssl.SSLContexts;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.jboss.resteasy.plugins.interceptors.encoding.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.encoding.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.encoding.GZIPEncodingInterceptor;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.ow2.proactive.web.WebProperties;


public class RestClient {

    private final ResteasyClient restEasyClient;

    private static SSLContext sslContext;

    private final String connectorIaasURL;

    public RestClient(String connectorIaasURL) {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                                                               .useSystemProperties()
                                                               .setRetryHandler(new StandardHttpRequestRetryHandler());
        setSSLContext(httpClientBuilder);
        ClientHttpEngine engine = new ApacheHttpClient4Engine(httpClientBuilder.build());

        ResteasyProviderFactory providerFactory = ResteasyProviderFactory.getInstance();
        registerGzipEncoding(providerFactory);
        this.restEasyClient = new ResteasyClientBuilder().providerFactory(providerFactory).httpEngine(engine).build();
        this.connectorIaasURL = connectorIaasURL;
    }

    public String getInfrastructures() {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures");
        Response response = target.request(MediaType.APPLICATION_JSON_TYPE).get();
        return checkAndGetResponse(response);
    }

    public String getInstancesByInfrastructure(String infrastructureId) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId +
                                                 "/instances");
        Response response = target.request(MediaType.APPLICATION_JSON_TYPE).get();
        return checkAndGetResponse(response);
    }

    public String postInfrastructures(String infrastructureJson) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures");
        Response response = target.request().post(Entity.entity(infrastructureJson, MediaType.APPLICATION_JSON_TYPE));
        return checkAndGetResponse(response);
    }

    public void deleteInfrastructure(String infrastructureId, boolean deleteInstances) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId);

        Response response;

        if (deleteInstances) {
            response = target.queryParam("deleteInstances", "true").request(MediaType.APPLICATION_JSON_TYPE).delete();
        } else {
            response = target.request(MediaType.APPLICATION_JSON_TYPE).delete();
        }

        checkAndGetResponse(response);
    }

    public String postKeyPairs(String infrastructureId, String instanceJson) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId +
                                                 "/keypairs");
        Response response = target.request().post(Entity.entity(instanceJson, MediaType.APPLICATION_JSON_TYPE));
        return checkAndGetResponse(response);
    }

    public String deleteKeyPair(String infrastructureId, String keyPairName, String region) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId +
                                                 "/keypairs");
        final MultivaluedMap<String, Object> queryParams = new MultivaluedHashMap<>();
        queryParams.add("keyPairName", keyPairName);
        queryParams.add("region", region);
        Response response = target.queryParams(queryParams).request(MediaType.APPLICATION_JSON_TYPE).delete();
        return checkAndGetResponse(response);
    }

    public String postInstances(String infrastructureId, String instanceJson) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId +
                                                 "/instances");
        Response response = target.request().post(Entity.entity(instanceJson, MediaType.APPLICATION_JSON_TYPE));
        return checkAndGetResponse(response);
    }

    public void deleteInstance(String infrastructureId, String key, String value) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId +
                                                 "/instances");
        Response response = target.queryParam(key, value).request(MediaType.APPLICATION_JSON_TYPE).delete();
        checkAndGetResponse(response);
    }

    public String postScript(String infrastructureId, String key, String value, String scriptJson) {
        ResteasyWebTarget target = initWebTarget(connectorIaasURL + "/infrastructures/" + infrastructureId +
                                                 "/instances/scripts");
        Response response = target.queryParam(key, value)
                                  .request()
                                  .post(Entity.entity(scriptJson, MediaType.APPLICATION_JSON_TYPE));
        return checkAndGetResponse(response);
    }

    private void registerGzipEncoding(ResteasyProviderFactory providerFactory) {
        if (!providerFactory.isRegistered(AcceptEncodingGZIPFilter.class)) {
            providerFactory.registerProvider(AcceptEncodingGZIPFilter.class);
        }
        if (!providerFactory.isRegistered(GZIPDecodingInterceptor.class)) {
            providerFactory.registerProvider(GZIPDecodingInterceptor.class);
        }
        if (!providerFactory.isRegistered(GZIPEncodingInterceptor.class)) {
            providerFactory.registerProvider(GZIPEncodingInterceptor.class);
        }
    }

    private Response checkResponseIsOK(Response response) {
        if (response.getStatus() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException(String.format("Failed: HTTP error code %s with error message: %s. ",
                                                     response.getStatus(),
                                                     response.readEntity(String.class)));
        }
        return response;
    }

    private ResteasyWebTarget initWebTarget(String url) {
        return restEasyClient.target(url);
    }

    private void setSSLContext(HttpClientBuilder httpClientBuilder) {
        if (WebProperties.WEB_HTTPS_ALLOW_ANY_CERTIFICATE.getValueAsBoolean()) {
            TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
            try {
                sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        } else {
            sslContext = SSLContexts.createSystemDefault();
        }
        HostnameVerifier hostnameVerifier;
        if (WebProperties.WEB_HTTPS_ALLOW_ANY_HOSTNAME.getValueAsBoolean()) {
            hostnameVerifier = NoopHostnameVerifier.INSTANCE;
        } else {
            hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        }
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
                                                                                               hostnameVerifier);
        httpClientBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
    }

    private String checkAndGetResponse(Response response) {
        try {
            return checkResponseIsOK(response).readEntity(String.class);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
}
