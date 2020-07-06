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
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONObject;


public class ConnectorIaasController {

    private static final Logger logger = Logger.getLogger(ConnectorIaasController.class);

    protected final ConnectorIaasClient connectorIaasClient;

    private final String infrastructureType;

    public ConnectorIaasController(String connectorIaasURL, String infrastructureType) {
        this.connectorIaasClient = new ConnectorIaasClient(ConnectorIaasClient.generateRestClient(connectorIaasURL));
        this.infrastructureType = infrastructureType;

    }

    public ConnectorIaasController(ConnectorIaasClient connectorIaasClient, String infrastructureType) {
        this.connectorIaasClient = connectorIaasClient;
        this.infrastructureType = infrastructureType;

    }

    public void waitForConnectorIaasToBeUP() {
        connectorIaasClient.waitForConnectorIaasToBeUP();
    }

    public String createInfrastructure(String infrastructureId, String username, String password, String endPoint,
            boolean destroyOnShutdown) {

        String infrastructureJson = ConnectorIaasJSONTransformer.getInfrastructureJSONWithEndPoint(infrastructureId,
                                                                                                   infrastructureType,
                                                                                                   username,
                                                                                                   password,
                                                                                                   endPoint,
                                                                                                   destroyOnShutdown);

        logger.info("Creating infrastructure : " + infrastructureJson);

        connectorIaasClient.createInfrastructure(infrastructureId, infrastructureJson);

        logger.info("Infrastructure created");

        return infrastructureId;
    }

    public String createInfrastructure(String infrastructureId, String username, String password, String endPoint,
            String region, boolean destroyOnShutdown) {

        String infrastructureJson = ConnectorIaasJSONTransformer.getInfrastructureJSONWithEndPoint(infrastructureId,
                                                                                                   infrastructureType,
                                                                                                   username,
                                                                                                   password,
                                                                                                   endPoint,
                                                                                                   region,
                                                                                                   destroyOnShutdown);

        logger.info("Creating infrastructure : " + infrastructureJson);

        connectorIaasClient.createInfrastructure(infrastructureId, infrastructureJson);

        logger.info("Infrastructure created");

        return infrastructureId;
    }

    public String createOpenstackInfrastructure(String infrastructureId, String username, String password,
            String domain, String scopePrefix, String scopeValue, String region, String identityVersion,
            String endPoint, boolean destroyOnShutdown) {

        String infrastructureJson = ConnectorIaasJSONTransformer.getOpenstackInfrastructureJSONWithEndPoint(infrastructureId,
                                                                                                            infrastructureType,
                                                                                                            username,
                                                                                                            password,
                                                                                                            domain,
                                                                                                            scopePrefix,
                                                                                                            scopeValue,
                                                                                                            region,
                                                                                                            identityVersion,
                                                                                                            endPoint,
                                                                                                            destroyOnShutdown);

        logger.info("Creating infrastructure : " +
                    String.format("{infrastructureId=%s,infrastructureType=%s,username=%s,domain=%s,scopePrefix=%s,scopeValue=%s,region=%s,identityVersion=%s,endpoint=%s,destroyOnShutdown=%s}",
                                  infrastructureId,
                                  infrastructureType,
                                  username,
                                  domain,
                                  scopePrefix,
                                  scopeValue,
                                  region,
                                  identityVersion,
                                  endPoint,
                                  destroyOnShutdown));

        connectorIaasClient.createInfrastructure(infrastructureId, infrastructureJson);

        logger.info("Infrastructure created");

        return infrastructureId;
    }

    public String createAzureInfrastructure(String infrastructureId, String clientId, String secret, String domain,
            String subscriptionId, String authenticationEndpoint, String managementEndpoint,
            String resourceManagerEndpoint, String graphEndpoint, boolean destroyOnShutdown) {

        String infrastructureJson = ConnectorIaasJSONTransformer.getAzureInfrastructureJSON(infrastructureId,
                                                                                            infrastructureType,
                                                                                            clientId,
                                                                                            secret,
                                                                                            domain,
                                                                                            subscriptionId,
                                                                                            authenticationEndpoint,
                                                                                            managementEndpoint,
                                                                                            resourceManagerEndpoint,
                                                                                            graphEndpoint,
                                                                                            destroyOnShutdown);

        logger.info("Creating Azure infrastructure : " +
                    String.format("{infrastructureId=%s,infrastructureType=%s,clientId=%s,domain=%s,subscriptionId=%s,authenticationEndpoint=%s,managementEndpoint=%s,resourceManagerEndpoint=%s,graphEndpoint=%s,destroyOnShutdown=%s}",
                                  infrastructureId,
                                  infrastructureType,
                                  clientId,
                                  domain,
                                  subscriptionId,
                                  authenticationEndpoint,
                                  resourceManagerEndpoint,
                                  graphEndpoint,
                                  destroyOnShutdown));

        connectorIaasClient.createInfrastructure(infrastructureId, infrastructureJson);

        logger.info("Azure infrastructure created");

        return infrastructureId;
    }

    public Set<String> createInstances(String infrastructureId, String instanceTag, String image, int numberOfInstances,
            int cores, int ram) {

        String instanceJson = ConnectorIaasJSONTransformer.getInstanceJSON(instanceTag,
                                                                           image,
                                                                           "" + numberOfInstances,
                                                                           "" + cores,
                                                                           "" + ram,
                                                                           null,
                                                                           null,
                                                                           null,
                                                                           null);

        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public Set<String> createAwsEc2Instances(String infrastructureId, String instanceTag, String image,
            int numberOfInstances, int cores, int ram, String username, String keyPairName) {

        String instanceJson = ConnectorIaasJSONTransformer.getAwsEc2InstanceJSON(instanceTag,
                                                                                 image,
                                                                                 "" + numberOfInstances,
                                                                                 "" + cores,
                                                                                 "" + ram,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 username,
                                                                                 keyPairName);

        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public Set<String> createAzureInstances(String infrastructureId, String instanceTag, String image,
            int numberOfInstances, String username, String password, String publicKey, String vmSizeType,
            String resourceGroup, String region, String privateNetworkCIDR, boolean staticPublicIP) {

        String instanceJson = ConnectorIaasJSONTransformer.getAzureInstanceJSON(instanceTag,
                                                                                image,
                                                                                "" + numberOfInstances,
                                                                                username,
                                                                                password,
                                                                                publicKey,
                                                                                vmSizeType,
                                                                                resourceGroup,
                                                                                region,
                                                                                privateNetworkCIDR,
                                                                                staticPublicIP);

        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public Set<String> createInstancesWithOptions(String infrastructureId, String instanceTag, String image,
            int numberOfInstances, int cores, int ram, String spotPrice, String securityGroupNames, String subnetId,
            String macAddresses) {

        String instanceJson = ConnectorIaasJSONTransformer.getInstanceJSON(instanceTag,
                                                                           image,
                                                                           "" + numberOfInstances,
                                                                           "" + cores,
                                                                           "" + ram,
                                                                           spotPrice,
                                                                           securityGroupNames,
                                                                           subnetId,
                                                                           macAddresses);

        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public Set<String> createAwsEc2InstancesWithOptions(String infrastructureId, String instanceTag, String image,
            int numberOfInstances, int cores, int ram, String spotPrice, String securityGroupNames, String subnetId,
            String macAddresses, String username, String publicKeyName) {

        String instanceJson = ConnectorIaasJSONTransformer.getAwsEc2InstanceJSON(instanceTag,
                                                                                 image,
                                                                                 "" + numberOfInstances,
                                                                                 "" + cores,
                                                                                 "" + ram,
                                                                                 spotPrice,
                                                                                 securityGroupNames,
                                                                                 subnetId,
                                                                                 macAddresses,
                                                                                 username,
                                                                                 publicKeyName);

        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public Set<String> createGCEInstances(String infrastructureId, String instanceTag, int numberOfInstances,
            String vmUsername, String vmPublicKey, String vmPrivateKey, List<String> initScripts, String image,
            String region, int ram, int cores) {
        String instanceJson = ConnectorIaasJSONTransformer.getGceInstanceJSON(instanceTag,
                                                                              String.valueOf(numberOfInstances),
                                                                              vmUsername,
                                                                              vmPublicKey,
                                                                              vmPrivateKey,
                                                                              initScripts,
                                                                              image,
                                                                              region,
                                                                              String.valueOf(ram),
                                                                              String.valueOf(cores));
        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public Set<String> createOpenstackInstance(String infrastructureId, String instanceTag, String image,
            int numberOfInstances, String hardwareType, String publicKeyName, List<String> scripts) {

        String instanceJson = ConnectorIaasJSONTransformer.getOpenstackInstanceJSON(instanceTag,
                                                                                    image,
                                                                                    String.valueOf(numberOfInstances),
                                                                                    publicKeyName,
                                                                                    hardwareType,
                                                                                    scripts);

        return createInstance(infrastructureId, instanceTag, instanceJson);
    }

    public void executeScript(String infrastructureId, String instanceId, List<String> scripts)
            throws ScriptNotExecutedException {
        executeScriptWithCredentials(infrastructureId, instanceId, scripts, null, null);

    }

    public void executeScriptWithCredentials(String infrastructureId, String instanceId, List<String> scripts,
            String username, String password) throws ScriptNotExecutedException {

        String instanceScriptJson = ConnectorIaasJSONTransformer.getScriptInstanceJSONWithCredentials(scripts,
                                                                                                      username,
                                                                                                      password);

        runScriptOnInstance(infrastructureId, instanceId, instanceScriptJson);
    }

    public void executeScriptWithKeyAuthentication(String infrastructureId, String instanceId, List<String> scripts,
            String username, String privateKey) throws ScriptNotExecutedException {

        String instanceScriptJson = ConnectorIaasJSONTransformer.getScriptInstanceJSONWithKeyAuthentication(scripts,
                                                                                                            username,
                                                                                                            privateKey);

        runScriptOnInstance(infrastructureId, instanceId, instanceScriptJson);
    }

    private void runScriptOnInstance(String infrastructureId, String instanceId, String instanceScriptJson)
            throws ScriptNotExecutedException {
        String scriptResult = null;
        try {

            logger.info("Trying to execute script for instance id:" + instanceId);

            scriptResult = connectorIaasClient.runScriptOnInstance(infrastructureId, instanceId, instanceScriptJson);

            logger.info("Executed successfully script for instance id:" + instanceId);
            logger.info("InstanceScriptJson: " + instanceScriptJson);
            logger.info("Script result: " + scriptResult);

        } catch (Exception e) {
            logger.error("Error while executing script:\n" + instanceScriptJson, e);
            throw new ScriptNotExecutedException(e);
        }
    }

    public void terminateInfrastructure(String infrastructureId, boolean deleteInstances) {
        connectorIaasClient.terminateInfrastructure(infrastructureId, deleteInstances);
    }

    public void terminateInstance(String infrastructureId, String instanceId) {
        logger.info("Deleting instance : " + instanceId + " in infrastructure " + infrastructureId);
        connectorIaasClient.terminateInstance(infrastructureId, instanceId);
    }

    public void terminateInstanceByTag(String infrastructureId, String instanceTag) {
        logger.info("Deleting instance by tag: " + instanceTag + " in infrastructure " + infrastructureId);
        connectorIaasClient.terminateInstanceByTag(infrastructureId, instanceTag);
    }

    private Set<String> createInstance(String infrastructureId, String instanceTag, String instanceJson) {
        Set<JSONObject> existingInstancesByInfrastructureId = connectorIaasClient.getAllJsonInstancesByInfrastructureId(infrastructureId);

        logger.info("Total existing Instances By Infrastructure Id : " + existingInstancesByInfrastructureId.size());

        Set<String> instancesIds = connectorIaasClient.createInstancesIfNotExist(infrastructureId,
                                                                                 instanceTag,
                                                                                 instanceJson,
                                                                                 existingInstancesByInfrastructureId);

        logger.info("Instances ids created : " + instancesIds);

        return instancesIds;
    }

    public SimpleImmutableEntry<String, String> createAwsEc2KeyPair(String infrastructureId, String instanceTag,
            String image, int numberOfInstances, int cores, int ram) {
        String instanceJson = ConnectorIaasJSONTransformer.getInstanceJSON(instanceTag,
                                                                           image,
                                                                           "" + numberOfInstances,
                                                                           "" + cores,
                                                                           "" + ram,
                                                                           null,
                                                                           null,
                                                                           null,
                                                                           null);
        return connectorIaasClient.createAwsEc2KeyPair(infrastructureId, instanceJson);
    }

    public void deleteKeyPair(String infrastructureId, String keyPairName, String region) {
        connectorIaasClient.deleteKeyPair(infrastructureId, keyPairName, region);
    }

}
