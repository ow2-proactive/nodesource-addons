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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.collect.Sets;


public class ConnectorIaasClient {

    private static final Logger logger = Logger.getLogger(ConnectorIaasClient.class);

    private static final int MAX_RETRIES_IN_CASE_OF_ERROR = 20;

    private static final int SLEEP_TIME_RETRIES_IN_CASE_OF_ERROR = 10000;

    private final RestClient restClient;

    public static RestClient generateRestClient(String connectorIaasURL) {
        return new RestClient(connectorIaasURL);
    }

    public ConnectorIaasClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void waitForConnectorIaasToBeUP() {
        int count = 0;
        while (true) {
            try {
                restClient.getInfrastructures();
                return;
            } catch (Exception e) {
                if (++count == MAX_RETRIES_IN_CASE_OF_ERROR) {
                    logger.error(e);
                    throw e;
                } else {
                    sleepFor(SLEEP_TIME_RETRIES_IN_CASE_OF_ERROR);
                }
            }
        }

    }

    public Set<JSONObject> getAllJsonInstancesByInfrastructureId(String infrastructureId) {
        Set<JSONObject> existingInstances = Sets.newHashSet();

        JSONArray instancesJSONObjects = new JSONArray(restClient.getInstancesByInfrastructure(infrastructureId));

        Iterator<Object> instancesJSONObjectsIterator = instancesJSONObjects.iterator();

        while (instancesJSONObjectsIterator.hasNext()) {
            existingInstances.add(((JSONObject) instancesJSONObjectsIterator.next()));
        }

        return existingInstances;
    }

    public String createInfrastructure(String infrastructureId, String infrastructureJson) {
        try {
            terminateInfrastructure(infrastructureId, false);
        } catch (RuntimeException re) {
            logger.error("Infrastructure termination runtime exception: ", re);
        }
        return restClient.postInfrastructures(infrastructureJson);
    }

    public Set<String> createInstancesIfNotExist(String infrastructureId, String instanceTag, String instanceJson,
            Set<JSONObject> existingInstances) {
        Set<String> instancesIds = getExistingInstanceIds(instanceTag, existingInstances);

        if (instancesIds.isEmpty()) {
            instancesIds = createInstances(infrastructureId, instanceJson);
        } else {
            logger.info("Existing instances have been found when trying to create instances. Reusing instance ids: " +
                        instancesIds);
        }

        return instancesIds;
    }

    private Set<String> getExistingInstanceIds(String instanceTag, Set<JSONObject> existingInstances) {
        Set<String> instancesIds = Sets.newHashSet();

        for (JSONObject instance : existingInstances) {
            if (instance.getString("tag").equals(instanceTag)) {
                instancesIds.add(instance.getString("id"));
            }
        }

        return instancesIds;
    }

    private Set<String> createInstances(String infrastructureId, String instanceJson) {
        String response = restClient.postInstances(infrastructureId, instanceJson);

        JSONArray instancesJSONObjects = new JSONArray(response);

        Set<String> instancesIds = Sets.newHashSet();

        Iterator<Object> instancesJSONObjectsIterator = instancesJSONObjects.iterator();

        while (instancesJSONObjectsIterator.hasNext()) {
            instancesIds.add(((JSONObject) instancesJSONObjectsIterator.next()).getString("id"));
        }

        return instancesIds;
    }

    public SimpleImmutableEntry<String, String> createAwsEc2KeyPair(String infrastructureId, String instanceJson) {
        String response = restClient.postKeyPairs(infrastructureId, instanceJson);

        JSONObject keyPairInfoJson = new JSONObject(response);

        if (keyPairInfoJson.length() > 0) {
            String keyPairName = keyPairInfoJson.keys().next();
            String keyPairPrivateKey = (String) keyPairInfoJson.get(keyPairName);
            return new SimpleImmutableEntry<>(keyPairName, keyPairPrivateKey);
        } else {
            throw new IllegalStateException("The received key pair information is empty");
        }
    }

    public void deleteKeyPair(String infrastructureId, String keyPairName, String region) {
        restClient.deleteKeyPair(infrastructureId, keyPairName, region);
    }

    public void terminateInfrastructure(String infrastructureId, boolean deleteInstances) {
        restClient.deleteInfrastructure(infrastructureId, deleteInstances);
    }

    public void terminateInstance(String infrastructureId, String instanceId) {
        restClient.deleteInstance(infrastructureId, "instanceId", instanceId);
    }

    public void terminateInstanceByTag(String infrastructureId, String instanceTag) {
        restClient.deleteInstance(infrastructureId, "instanceTag", instanceTag);
    }

    public String runScriptOnInstance(String infrastructureId, String instanceId, String instanceScriptJson) {
        int count = 0;
        while (true) {
            try {
                return restClient.postScript(infrastructureId, "instanceId", instanceId, instanceScriptJson);
            } catch (Exception e) {
                if (++count == MAX_RETRIES_IN_CASE_OF_ERROR) {
                    logger.error(e);
                    throw e;
                } else {
                    sleepFor(SLEEP_TIME_RETRIES_IN_CASE_OF_ERROR);
                }
            }
        }

    }

    private void sleepFor(long millisecondsToSleep) {
        try {
            Thread.sleep(millisecondsToSleep);
        } catch (InterruptedException e) {
            logger.warn("Failed to sleep", e);
        }
    }

}
