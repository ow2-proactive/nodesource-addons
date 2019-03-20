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

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;


public class RestClient {

    private final ResteasyClient restEasyClient;

    private final String connectorIaasURL;

    public RestClient(String connectorIaasURL) {
        this.restEasyClient = new ResteasyClientBuilder().build();
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

    private Response checkResponseIsOK(Response response) {
        if (response.getStatus() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
        }
        return response;
    }

    private ResteasyWebTarget initWebTarget(String url) {
        return restEasyClient.target(url);
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
