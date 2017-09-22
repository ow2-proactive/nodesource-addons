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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;

import com.google.common.collect.Maps;


public class AzureScaleSetInfrastructure extends InfrastructureManager {

    private static final Logger LOGGER = Logger.getLogger(AzureScaleSetInfrastructure.class);

    public static final String INSTANCE_ID_NODE_PROPERTY = "instanceId";

    public static final String INFRASTRUCTURE_TYPE = "azureScaleSet";

    private static final String DEFAULT_RM_HOSTNAME = "localhost";

    private final static int PARAMETERS_NUMBER = 23;

    // Indexes of parameters
    private final static int CLIENT_ID_INDEX = 0;

    private final static int SECRET_INDEX = 1;

    private final static int DOMAIN_INDEX = 2;

    private final static int SUBSCRIPTION_ID_INDEX = 3;

    private final static int AUTHENTICATION_ENDPOINT_INDEX = 4;

    private final static int MANAGEMENT_ENDPOINT_INDEX = 5;

    private final static int RESOURCE_MANAGER_ENDPOINT_INDEX = 6;

    private final static int GRAPH_ENDPOINT_INDEX = 7;

    private final static int RM_HOSTNAME_INDEX = 8;

    private final static int CONNECTOR_IAAS_URL_INDEX = 9;

    private final static int IMAGE_INDEX = 10;

    private final static int VM_SIZE_TYPE_INDEX = 11;

    private final static int VM_USERNAME_INDEX = 12;

    private final static int VM_PASSWORD_INDEX = 13;

    private final static int VM_PUBLIC_KEY_INDEX = 14;

    private final static int RESOURCE_GROUP_INDEX = 15;

    private final static int REGION_INDEX = 16;

    private final static int NUMBER_OF_INSTANCES_INDEX = 17;

    private final static int NUMBER_OF_NODES_PER_INSTANCE_INDEX = 18;

    private final static int CUSTOM_SCRIPT_URL_INDEX = 19;

    private final static int PRIVATE_NETWORK_CIDR_INDEX = 20;

    private final static int STATIC_PUBLIC_IP_INDEX = 21;

    private final static int ADDITIONAL_PROPERTIES_INDEX = 22;

    // Command lines patterns
    private static final CharSequence RM_HOSTNAME_PATTERN = "<RM_HOSTNAME>";

    private static final CharSequence RM_URL_PATTERN = "<RM_URL>";

    private static final CharSequence COMMUNICATION_PROTOCOL_PATTERN = "<COMMUNICATION_PROTOCOL>";

    private static final CharSequence INSTANCE_ID_PATTERN = "<INSTANCE_ID>";

    private static final CharSequence ADDITIONAL_PROPERTIES_PATTERN = "<ADDITIONAL_PROPERTIES>";

    private static final CharSequence NODESOURCE_NAME_PATTERN = "<NODESOURCE_NAME>";

    private static final CharSequence NUMBER_OF_NODES_PATTERN = "<NUMBER_OF_NODES>";

    @Configurable(description = "The Azure clientId")
    protected String clientId = null;

    @Configurable(description = "The Azure secret key")
    protected String secret = null;

    @Configurable(description = "The Azure domain or tenantId")
    protected String domain = null;

    @Configurable(description = "The Azure subscriptionId to use (if not specified, it will try to use the default one)")
    protected String subscriptionId = null;

    @Configurable(description = "Optional authentication endpoint from specific Azure environment")
    protected String authenticationEndpoint = null;

    @Configurable(description = "Optional management endpoint from specific Azure environment")
    protected String managementEndpoint = null;

    @Configurable(description = "Optional resource manager endpoint from specific Azure environment")
    protected String resourceManagerEndpoint = null;

    @Configurable(description = "Optional graph endpoint from specific Azure environment")
    protected String graphEndpoint = null;

    @Configurable(description = "Resource manager hostname or ip address")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = "http://" + generateDefaultRMHostname() + ":8080/connector-iaas";

    @Configurable(description = "Image (name or key)")
    protected String image = null;

    @Configurable(description = "Azure virtual machine size type (by default: 'Standard_D1_v2')")
    protected String vmSizeType = null;

    @Configurable(description = "The virtual machine Username")
    protected String vmUsername = null;

    @Configurable(description = "The virtual machine Password (must be 12 char minimum long, with at least 1 digit, 1 lower, 1 upper and 1 special char)")
    protected String vmPassword = null;

    @Configurable(description = "A public key to allow SSH connection to the VM")
    protected String vmPublicKey = null;

    @Configurable(description = "The Azure resourceGroup to use (if not specified, the one from the image will be used)")
    protected String resourceGroup = null;

    @Configurable(description = "The Azure Region to use (if not specified, the one from the image will be used)")
    protected String region = null;

    @Configurable(description = "Total instance to create")
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Command used to download the worker jar")
    protected String customScriptURL = "http://8080/customScript.sh";

    @Configurable(description = "Optional network CIDR to attach with new VM(s) (by default: '10.0.0.0/24')")
    protected String privateNetworkCIDR = null;

    @Configurable(description = "Optional flag to specify if the public IP(s) of the new VM(s) must be static ('true' by default)")
    protected boolean staticPublicIP = true;

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    protected ConnectorIaasController connectorIaasController = null;

    protected final Map<String, Set<String>> nodesPerInstances;

    /**
     * Default constructor
     */
    public AzureScaleSetInfrastructure() {
        nodesPerInstances = Maps.newConcurrentMap();
    }

    @Override
    public void configure(Object... parameters) {

        LOGGER.info("Validating parameters : " + Arrays.toString(parameters));
        validate(parameters);

        this.clientId = getParameter(parameters, CLIENT_ID_INDEX);
        this.secret = getParameter(parameters, SECRET_INDEX);
        this.domain = getParameter(parameters, DOMAIN_INDEX);
        this.subscriptionId = getParameter(parameters, SUBSCRIPTION_ID_INDEX);
        this.authenticationEndpoint = getParameter(parameters, AUTHENTICATION_ENDPOINT_INDEX);
        this.managementEndpoint = getParameter(parameters, MANAGEMENT_ENDPOINT_INDEX);
        this.resourceManagerEndpoint = getParameter(parameters, RESOURCE_MANAGER_ENDPOINT_INDEX);
        this.graphEndpoint = getParameter(parameters, GRAPH_ENDPOINT_INDEX);
        this.rmHostname = getParameter(parameters, RM_HOSTNAME_INDEX);
        this.connectorIaasURL = getParameter(parameters, CONNECTOR_IAAS_URL_INDEX);
        this.image = getParameter(parameters, IMAGE_INDEX);
        this.vmSizeType = getParameter(parameters, VM_SIZE_TYPE_INDEX);
        this.vmUsername = getParameter(parameters, VM_USERNAME_INDEX);
        this.vmPassword = getParameter(parameters, VM_PASSWORD_INDEX);
        this.vmPublicKey = getParameter(parameters, VM_PUBLIC_KEY_INDEX);
        this.resourceGroup = getParameter(parameters, RESOURCE_GROUP_INDEX);
        this.region = getParameter(parameters, REGION_INDEX);
        this.numberOfInstances = Integer.parseInt(getParameter(parameters, NUMBER_OF_INSTANCES_INDEX));
        this.numberOfNodesPerInstance = Integer.parseInt(getParameter(parameters, NUMBER_OF_NODES_PER_INSTANCE_INDEX));
        this.customScriptURL = getParameter(parameters, CUSTOM_SCRIPT_URL_INDEX);
        this.privateNetworkCIDR = getParameter(parameters, PRIVATE_NETWORK_CIDR_INDEX);
        this.staticPublicIP = Boolean.parseBoolean(getParameter(parameters, STATIC_PUBLIC_IP_INDEX));
        this.additionalProperties = getParameter(parameters, ADDITIONAL_PROPERTIES_INDEX);

        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    private String getParameter(Object[] parameters, int index) {
        return parameters[index].toString().trim();
    }

    private void validate(Object[] parameters) {
        if (parameters == null || parameters.length < PARAMETERS_NUMBER) {
            throw new IllegalArgumentException("Invalid parameters for AzureScaleSetInfrastructure creation");
        }

        throwIllegalArgumentExceptionIfNull(parameters[CLIENT_ID_INDEX], "Azure clientId must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[SECRET_INDEX], "Azure secret key must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[DOMAIN_INDEX], "Azure domain or tenantId must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[RM_HOSTNAME_INDEX],
                                            "The Resource manager hostname must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[CONNECTOR_IAAS_URL_INDEX],
                                            "The connector-iaas URL must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[IMAGE_INDEX], "The image id must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[VM_USERNAME_INDEX],
                                            "The virtual machine username must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[VM_PASSWORD_INDEX],
                                            "The virtual machine password must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[NUMBER_OF_INSTANCES_INDEX],
                                            "The number of instances to create must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[NUMBER_OF_NODES_PER_INSTANCE_INDEX],
                                            "The number of nodes per instance to deploy must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[CUSTOM_SCRIPT_URL_INDEX],
                                            "The download node.jar command must be specified");

        if (parameters[ADDITIONAL_PROPERTIES_INDEX] == null) {
            parameters[ADDITIONAL_PROPERTIES_INDEX] = "";
        }
    }

    private void throwIllegalArgumentExceptionIfNull(Object parameter, String error) {
        if (parameter == null) {
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public void acquireNode() {

        connectorIaasController.waitForConnectorIaasToBeUP();

        connectorIaasController.createAzureInfrastructure(getInfrastructureId(),
                                                          clientId,
                                                          secret,
                                                          domain,
                                                          subscriptionId,
                                                          authenticationEndpoint,
                                                          managementEndpoint,
                                                          resourceManagerEndpoint,
                                                          graphEndpoint,
                                                          false);

        String instanceTag = getInfrastructureId();
        Set<String> instancesIds;
        instancesIds = connectorIaasController.createAzureScaleSet(getInfrastructureId(),
                                                                   instanceTag,
                                                                   image,
                                                                   numberOfInstances,
                                                                   vmUsername,
                                                                   vmPassword,
                                                                   vmPublicKey,
                                                                   vmSizeType,
                                                                   resourceGroup,
                                                                   region,
                                                                   privateNetworkCIDR,
                                                                   staticPublicIP,
                                                                   customScriptURL);

        LOGGER.info("Instances ids created : " + instancesIds);

    }

    @Override
    public void acquireAllNodes() {
        acquireNode();
    }

    @Override
    public void removeNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        try {
            node.getProActiveRuntime().killNode(node.getNodeInformation().getName());

        } catch (Exception e) {
            LOGGER.warn("Unable to remove the node '" + node.getNodeInformation().getName() + "' with error: " + e);
        }

        synchronized (this) {
            nodesPerInstances.get(instanceId).remove(node.getNodeInformation().getName());
            LOGGER.info("Removed node : " + node.getNodeInformation().getName());

            if (nodesPerInstances.get(instanceId).isEmpty()) {
                connectorIaasController.terminateInstance(getInfrastructureId(), instanceId);
                nodesPerInstances.remove(instanceId);
                LOGGER.info("Removed instance : " + instanceId);
            }
        }
    }

    @Override
    public void notifyAcquiredNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        synchronized (this) {
            if (!nodesPerInstances.containsKey(instanceId)) {
                nodesPerInstances.put(instanceId, new HashSet<String>());
            }
            nodesPerInstances.get(instanceId).add(node.getNodeInformation().getName());
        }
    }

    @Override
    public String getDescription() {
        return "Handles nodes from Microsoft Azure.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getDescription();
    }

    private String generateDefaultRMHostname() {
        try {
            // best effort, may not work for all machines
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to retrieve local canonical hostname with error: " + e);
            return DEFAULT_RM_HOSTNAME;
        }
    }

    private String getInstanceIdProperty(Node node) throws RMException {
        try {
            return node.getProperty(INSTANCE_ID_NODE_PROPERTY);
        } catch (ProActiveException e) {
            throw new RMException(e);
        }
    }

    private String getInfrastructureId() {
        return nodeSource.getName().trim().replace(" ", "_").toLowerCase();
    }

}
