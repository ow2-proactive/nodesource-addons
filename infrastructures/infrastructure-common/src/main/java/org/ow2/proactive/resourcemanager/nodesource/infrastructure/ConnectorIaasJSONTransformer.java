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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;


public class ConnectorIaasJSONTransformer {

    private ConnectorIaasJSONTransformer() {
    }

    public static String getInfrastructureJSONWithEndPoint(String infrastructureId, String type, String username,
            String password, String endpoint, boolean toBeRemovedOnShutdown) {
        JSONObject credentials = new JSONObject();
        credentials.put("username", username);
        credentials.put("password", password);
        JSONObject infrastructure = new JSONObject().put("id", infrastructureId)
                                                    .put("type", type)
                                                    .put("credentials", credentials)
                                                    .put("toBeRemovedOnShutdown", toBeRemovedOnShutdown);

        infrastructure = infrastructure.put("endpoint", endpoint);

        return infrastructure.toString();
    }

    public static String getInfrastructureJSONWithEndPoint(String infrastructureId, String type, String username,
            String password, String endpoint, String region, boolean toBeRemovedOnShutdown) {
        JSONObject credentials = new JSONObject();
        credentials.put("username", username);
        credentials.put("password", password);
        JSONObject infrastructure = new JSONObject().put("id", infrastructureId)
                                                    .put("type", type)
                                                    .put("region", region)
                                                    .put("credentials", credentials)
                                                    .put("toBeRemovedOnShutdown", toBeRemovedOnShutdown);

        infrastructure = infrastructure.put("endpoint", endpoint);

        return infrastructure.toString();
    }

    public static String getOpenstackInfrastructureJSONWithEndPoint(String infrastructureId, String type,
            String username, String password, String domain, String scopePrefix, String scopeValue, String region,
            String identityVersion, String endpoint, boolean toBeRemovedOnShutdown) {
        JSONObject credentials = new JSONObject();
        credentials.put("username", username);
        credentials.put("password", password);
        credentials.put("domain", domain);
        JSONObject scope = new JSONObject();
        scope.put("prefix", scopePrefix);
        scope.put("value", scopeValue);

        JSONObject infrastructure = new JSONObject().put("id", infrastructureId)
                                                    .put("type", type)
                                                    .put("credentials", credentials)
                                                    .put("scope", scope)
                                                    .put("region", region)
                                                    .put("identityVersion", identityVersion)
                                                    .put("toBeRemovedOnShutdown", toBeRemovedOnShutdown);

        infrastructure = infrastructure.put("endpoint", endpoint);

        return infrastructure.toString();
    }

    public static String getAzureInfrastructureJSON(String infrastructureId, String type, String clientId,
            String secret, String domain, String subscriptionId, String authenticationEndpoint,
            String managementEndpoint, String resourceManagerEndpoint, String graphEndpoint,
            boolean toBeRemovedOnShutdown) {
        JSONObject credentials = new JSONObject().put("username", clientId).put("password", secret).put("domain",
                                                                                                        domain);
        if (subscriptionId != null && !subscriptionId.isEmpty()) {
            credentials.put("subscriptionId", subscriptionId);
        }

        JSONObject infrastructure = new JSONObject().put("id", infrastructureId).put("type", type).put("credentials",
                                                                                                       credentials);
        if (authenticationEndpoint != null && !authenticationEndpoint.isEmpty()) {
            infrastructure.put("authenticationEndpoint", authenticationEndpoint);
        }
        if (managementEndpoint != null && !managementEndpoint.isEmpty()) {
            infrastructure.put("managementEndpoint", managementEndpoint);
        }
        if (resourceManagerEndpoint != null && !resourceManagerEndpoint.isEmpty()) {
            infrastructure.put("resourceManagerEndpoint", resourceManagerEndpoint);
        }
        if (graphEndpoint != null && !graphEndpoint.isEmpty()) {
            infrastructure.put("graphEndpoint", graphEndpoint);
        }
        infrastructure.put("toBeRemovedOnShutdown", toBeRemovedOnShutdown);

        return infrastructure.toString();
    }

    public static String getInstanceJSON(String tag, String image, String number, String cpu, String ram,
            String spotPrice, String securityGroupNames, String subnetId, String macAddresses) {
        JSONObject hardware = new JSONObject();
        hardware.put("minCores", cpu);
        hardware.put("minRam", ram);

        JSONObject options = new JSONObject();

        if (spotPrice != null && !spotPrice.isEmpty()) {
            options.put("spotPrice", spotPrice);
        }

        if (securityGroupNames != null && !securityGroupNames.isEmpty()) {
            String[] groups = securityGroupNames.split(",");
            Set<String> securityGroupNamesSet = new HashSet<String>(Arrays.asList(groups));
            options.put("securityGroupNames", securityGroupNamesSet);
        }

        if (subnetId != null && !subnetId.isEmpty()) {
            options.put("subnetId", subnetId);
        }

        if (macAddresses != null && !macAddresses.isEmpty()) {
            String[] addresses = macAddresses.split(",");
            List<String> addressesList = Arrays.asList(addresses);
            options.put("macAddresses", addressesList);
        }

        return new JSONObject().put("tag", tag)
                               .put("image", image)
                               .put("number", number)
                               .put("hardware", hardware)
                               .put("options", options)
                               .toString();
    }

    public static String getAzureInstanceJSON(String instanceTag, String image, String number, String username,
            String password, String publickey, String vmSizeType, String resourceGroup, String region,
            String privateNetworkCIDR, boolean staticPublicIP) {
        JSONObject hardware = new JSONObject();
        if (vmSizeType != null && !vmSizeType.isEmpty()) {
            hardware.put("type", vmSizeType);
        }

        JSONObject credentials = new JSONObject();
        if (username != null && !username.isEmpty()) {
            credentials.put("username", username);
        }
        if (password != null && !password.isEmpty()) {
            credentials.put("password", password);
        }
        if (publickey != null && !publickey.isEmpty()) {
            credentials.put("publicKey", publickey);
        }

        JSONObject options = new JSONObject();
        if (resourceGroup != null && !resourceGroup.isEmpty()) {
            options.put("resourceGroup", resourceGroup);
        }
        if (region != null && !region.isEmpty()) {
            options.put("region", region);
        }
        if (privateNetworkCIDR != null && !privateNetworkCIDR.isEmpty()) {
            options.put("privateNetworkCIDR", privateNetworkCIDR);
        }
        options.put("staticPublicIP", staticPublicIP);

        JSONObject instance = new JSONObject().put("tag", instanceTag).put("image", image).put("number", number);
        if (hardware.length() > 0) {
            instance.put("hardware", hardware);
        }
        if (credentials.length() > 0) {
            instance.put("credentials", credentials);
        }
        if (options.length() > 0) {
            instance.put("options", options);
        }
        return instance.toString();
    }

    public static String getAwsEc2InstanceJSON(String tag, String image, String number, String cpu, String ram,
            String spotPrice, String securityGroupNames, String subnetId, String macAddresses, String username,
            String keyPairName) {
        JSONObject hardware = new JSONObject();
        hardware.put("minCores", cpu);
        hardware.put("minRam", ram);

        JSONObject options = new JSONObject();

        if (spotPrice != null && !spotPrice.isEmpty()) {
            options.put("spotPrice", spotPrice);
        }

        if (securityGroupNames != null && !securityGroupNames.isEmpty()) {
            String[] groups = securityGroupNames.split(",");
            Set<String> securityGroupNamesSet = new HashSet<String>(Arrays.asList(groups));
            options.put("securityGroupNames", securityGroupNamesSet);
        }

        if (subnetId != null && !subnetId.isEmpty()) {
            options.put("subnetId", subnetId);
        }

        if (macAddresses != null && !macAddresses.isEmpty()) {
            String[] addresses = macAddresses.split(",");
            List<String> addressesList = Arrays.asList(addresses);
            options.put("macAddresses", addressesList);
        }

        JSONObject instance = new JSONObject().put("tag", tag)
                                              .put("image", image)
                                              .put("number", number)
                                              .put("hardware", hardware)
                                              .put("options", options);

        JSONObject credentials = new JSONObject();
        if (username != null && !username.isEmpty()) {
            credentials.put("username", username);
        }
        if (keyPairName != null && !keyPairName.isEmpty()) {
            credentials.put("publicKeyName", keyPairName);
        }
        if (credentials.length() > 0) {
            instance.put("credentials", credentials);
        }

        return instance.toString();
    }

    public static String getGceInstanceJSON(String tag, String number, String vmUsername, String vmPublicKey,
            String vmPrivateKey, List<String> initScripts, String image, String region, String ram, String cores) {
        JSONObject credentials = new JSONObject().put("username", vmUsername)
                                                 .put("publicKey", vmPublicKey)
                                                 .put("privateKey", vmPrivateKey);
        JSONObject scripts = new JSONObject().put("scripts", new JSONArray(initScripts));
        JSONObject options = new JSONObject().put("region", region);
        JSONObject hardware = new JSONObject().put("minRam", ram).put("minCores", cores);
        JSONObject instance = new JSONObject().put("tag", tag)
                                              .put("number", number)
                                              .put("image", image)
                                              .put("credentials", credentials)
                                              .put("initScript", scripts)
                                              .put("options", options)
                                              .put("hardware", hardware);
        return instance.toString();
    }

    public static String getOpenstackInstanceJSON(String tag, String image, String number, String publicKeyName,
            String type, List<String> scripts) {

        JSONObject credentials = new JSONObject();
        if (publicKeyName != null && !publicKeyName.equals("")) {
            credentials.put("publicKeyName", publicKeyName);
        }
        JSONObject hardware = new JSONObject();
        hardware.put("type", type);
        JSONObject script = new JSONObject();
        script.put("scripts", new JSONArray(scripts));
        return new JSONObject().put("tag", tag)
                               .put("image", image)
                               .put("number", number)
                               .put("credentials", credentials)
                               .put("hardware", hardware)
                               .put("initScript", script)
                               .toString();
    }

    public static String getScriptInstanceJSONWithCredentials(List<String> scripts, String username, String password) {
        JSONObject scriptObject = new JSONObject().put("scripts", new JSONArray(scripts));
        if (username != null && password != null) {
            JSONObject credentials = new JSONObject();
            credentials.put("username", username);
            credentials.put("password", password);
            scriptObject = scriptObject.put("credentials", credentials);
        }
        return scriptObject.toString();
    }

    public static String getScriptInstanceJSONWithKeyAuthentication(List<String> scripts, String username,
            String privateKey) {
        JSONObject scriptObject = new JSONObject().put("scripts", new JSONArray(scripts));
        if (username != null || privateKey != null) {
            JSONObject credentials = new JSONObject();
            if (username != null) {
                credentials.put("username", username);
            }
            if (privateKey != null) {
                credentials.put("privateKey", privateKey);
            }
            scriptObject = scriptObject.put("credentials", credentials);
        }
        return scriptObject.toString();
    }

}
