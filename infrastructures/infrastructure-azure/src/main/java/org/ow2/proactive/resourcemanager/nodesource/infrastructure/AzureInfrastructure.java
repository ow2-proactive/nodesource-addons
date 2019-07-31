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

import static org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties.RM_CLOUD_INFRASTRUCTURES_DESTROY_INSTANCES_ON_SHUTDOWN;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.LinuxInitScriptGenerator;

import lombok.Getter;


public class AzureInfrastructure extends AbstractAddonInfrastructure {

    private static final Logger LOGGER = Logger.getLogger(AzureInfrastructure.class);

    public static final String WINDOWS = "windows";

    public static final String LINUX = "linux";

    @Getter
    private final String instanceIdNodeProperty = "instanceId";

    public static final String INFRASTRUCTURE_TYPE = "azure";

    private static final String DEFAULT_HTTP_RM_URL = "http://localhost:8080";

    private static final String DEFAULT_ADDITIONAL_PROPERTIES = "-Dproactive.useIPaddress=true -Dproactive.net.public_address=$(wget -qO- ipinfo.io/ip) -Dproactive.pnp.port=64738";

    private final static int PARAMETERS_NUMBER = 24;

    // Indexes of parameters
    private final static int CLIENT_ID_INDEX = 0;

    private final static int SECRET_INDEX = 1;

    private final static int DOMAIN_INDEX = 2;

    private final static int SUBSCRIPTION_ID_INDEX = 3;

    private final static int AUTHENTICATION_ENDPOINT_INDEX = 4;

    private final static int MANAGEMENT_ENDPOINT_INDEX = 5;

    private final static int RESOURCE_MANAGER_ENDPOINT_INDEX = 6;

    private final static int GRAPH_ENDPOINT_INDEX = 7;

    private final static int RM_HTTP_URL_INDEX = 8;

    private final static int CONNECTOR_IAAS_URL_INDEX = 9;

    private final static int IMAGE_INDEX = 10;

    private final static int IMAGE_OS_TYPE_INDEX = 11;

    private final static int VM_SIZE_TYPE_INDEX = 12;

    private final static int VM_USERNAME_INDEX = 13;

    private final static int VM_PASSWORD_INDEX = 14;

    private final static int VM_PUBLIC_KEY_INDEX = 15;

    private final static int RESOURCE_GROUP_INDEX = 16;

    private final static int REGION_INDEX = 17;

    private final static int NUMBER_OF_INSTANCES_INDEX = 18;

    private final static int NUMBER_OF_NODES_PER_INSTANCE_INDEX = 19;

    private final static int DOWNLOAD_COMMAND_INDEX = 20;

    private final static int PRIVATE_NETWORK_CIDR_INDEX = 21;

    private final static int STATIC_PUBLIC_IP_INDEX = 22;

    private final static int ADDITIONAL_PROPERTIES_INDEX = 23;

    // Command lines patterns
    private static final CharSequence RM_HTTP_URL_PATTERN = "<RM_HTTP_URL>";

    private static final CharSequence RM_URL_PATTERN = "<RM_URL>";

    private static final CharSequence INSTANCE_ID_PATTERN = "<INSTANCE_ID>";

    private static final CharSequence ADDITIONAL_PROPERTIES_PATTERN = "<ADDITIONAL_PROPERTIES>";

    private static final CharSequence NODESOURCE_NAME_PATTERN = "<NODESOURCE_NAME>";

    private static final CharSequence NUMBER_OF_NODES_PATTERN = "<NUMBER_OF_NODES>";

    // Command lines definition
    private static final String POWERSHELL_DOWNLOAD_CMD = "$wc = New-Object System.Net.WebClient; $wc.DownloadFile('" +
                                                          RM_HTTP_URL_PATTERN + "/rest/node.jar'" + ", 'node.jar')";

    private static final String WGET_DOWNLOAD_CMD = "wget -nv " + RM_HTTP_URL_PATTERN + "/rest/node.jar";

    private final String START_NODE_CMD = "java -jar node.jar -Dproactive.pamr.router.address=" + RM_HTTP_URL_PATTERN +
                                          " -D" + instanceIdNodeProperty + "=" + INSTANCE_ID_PATTERN + " " +
                                          ADDITIONAL_PROPERTIES_PATTERN + " -r " + RM_URL_PATTERN + " -s " +
                                          NODESOURCE_NAME_PATTERN + " -w " + NUMBER_OF_NODES_PATTERN;

    private final String START_NODE_FALLBACK_CMD = "java -jar node.jar -D" + instanceIdNodeProperty + "=" +
                                                   INSTANCE_ID_PATTERN + " " + ADDITIONAL_PROPERTIES_PATTERN + " -r " +
                                                   RM_URL_PATTERN + " -s " + NODESOURCE_NAME_PATTERN + " -w " +
                                                   NUMBER_OF_NODES_PATTERN;

    private final transient LinuxInitScriptGenerator linuxInitScriptGenerator = new LinuxInitScriptGenerator();

    @Configurable(description = "The Azure clientId", sectionSelector = 1, important = true)
    protected String clientId = null;

    @Configurable(description = "The Azure secret key", sectionSelector = 1, important = true)
    protected String secret = null;

    @Configurable(description = "The Azure domain or tenantId", sectionSelector = 1, important = true)
    protected String domain = null;

    @Configurable(description = "The Azure subscriptionId to use (if not specified, it will try to use the default one)", sectionSelector = 1, important = true)
    protected String subscriptionId = null;

    @Configurable(description = "Optional authentication endpoint from specific Azure environment", sectionSelector = 2)
    protected String authenticationEndpoint = null;

    @Configurable(description = "Optional management endpoint from specific Azure environment", sectionSelector = 2)
    protected String managementEndpoint = null;

    @Configurable(description = "Optional resource manager endpoint from specific Azure environment", sectionSelector = 2)
    protected String resourceManagerEndpoint = null;

    @Configurable(description = "Optional graph endpoint from specific Azure environment", sectionSelector = 2)
    protected String graphEndpoint = null;

    @Configurable(description = "Resource manager HTTP URL (must be accessible from nodes)", sectionSelector = 3, important = true)
    protected String rmHttpUrl = generateDefaultHttpRMUrl();

    @Configurable(description = "Connector-iaas URL", sectionSelector = 3, important = true)
    protected String connectorIaasURL = generateDefaultHttpRMUrl() + "/connector-iaas";

    @Configurable(description = "Image (name or key)", sectionSelector = 5, important = true)
    protected String image = null;

    @Configurable(description = "Image OS type (choose between 'linux' and 'windows', default: 'linux')", sectionSelector = 5, important = true)
    protected String imageOSType = LINUX;

    @Configurable(description = "Azure virtual machine size type (by default: 'Standard_D1_v2')", sectionSelector = 5, important = true)
    protected String vmSizeType = null;

    @Configurable(description = "The virtual machine Username", sectionSelector = 5, important = true)
    protected String vmUsername = null;

    @Configurable(description = "The virtual machine Password", password = true, sectionSelector = 5, important = true)
    protected String vmPassword = null;

    @Configurable(description = "A public key to allow SSH connection to the VM", sectionSelector = 5)
    protected String vmPublicKey = null;

    @Configurable(description = "The Azure resourceGroup to use (if not specified, the one from the image will be used)", sectionSelector = 5, important = true)
    protected String resourceGroup = null;

    @Configurable(description = "The Azure Region to use (if not specified, the one from the image will be used)", sectionSelector = 5)
    protected String region = null;

    @Configurable(description = "Total instance to create", sectionSelector = 4, important = true)
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance", sectionSelector = 4, important = true)
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Command used to download the worker jar (a default command will be generated for the specified image OS type)", sectionSelector = 7)
    protected String downloadCommand = null;

    @Configurable(description = "Optional network CIDR to attach with new VM(s) (by default: '10.0.0.0/24')", sectionSelector = 6)
    protected String privateNetworkCIDR = null;

    @Configurable(description = "Optional flag to specify if the public IP(s) of the new VM(s) must be static ('true' by default)", checkbox = true, sectionSelector = 6)
    protected boolean staticPublicIP = true;

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")", sectionSelector = 7)
    protected String additionalProperties = DEFAULT_ADDITIONAL_PROPERTIES;

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
        this.rmHttpUrl = getParameter(parameters, RM_HTTP_URL_INDEX);
        this.connectorIaasURL = getParameter(parameters, CONNECTOR_IAAS_URL_INDEX);
        this.image = getParameter(parameters, IMAGE_INDEX);
        this.imageOSType = getParameter(parameters, IMAGE_OS_TYPE_INDEX).toLowerCase();
        this.vmSizeType = getParameter(parameters, VM_SIZE_TYPE_INDEX);
        this.vmUsername = getParameter(parameters, VM_USERNAME_INDEX);
        this.vmPassword = getParameter(parameters, VM_PASSWORD_INDEX);
        this.vmPublicKey = getParameter(parameters, VM_PUBLIC_KEY_INDEX);
        this.resourceGroup = getParameter(parameters, RESOURCE_GROUP_INDEX);
        this.region = getParameter(parameters, REGION_INDEX);
        this.numberOfInstances = Integer.parseInt(getParameter(parameters, NUMBER_OF_INSTANCES_INDEX));
        this.numberOfNodesPerInstance = Integer.parseInt(getParameter(parameters, NUMBER_OF_NODES_PER_INSTANCE_INDEX));
        this.downloadCommand = getParameter(parameters, DOWNLOAD_COMMAND_INDEX);
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
            throw new IllegalArgumentException("Invalid parameters for AzureInfrastructure creation");
        }

        throwIllegalArgumentExceptionIfNull(parameters[CLIENT_ID_INDEX], "Azure clientId must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[SECRET_INDEX], "Azure secret key must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[DOMAIN_INDEX], "Azure domain or tenantId must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[RM_HTTP_URL_INDEX],
                                            "The Resource manager HTTP URL must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[CONNECTOR_IAAS_URL_INDEX],
                                            "The connector-iaas URL must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[IMAGE_INDEX], "The image id must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[IMAGE_OS_TYPE_INDEX], "The image OS type must be specified");
        if (!getParameter(parameters, IMAGE_OS_TYPE_INDEX).equalsIgnoreCase(WINDOWS) &&
            !getParameter(parameters, IMAGE_OS_TYPE_INDEX).equalsIgnoreCase(LINUX)) {
            throw new IllegalArgumentException("The image OS type is not recognized, it must be 'windows' or 'linux'");
        }
        throwIllegalArgumentExceptionIfNull(parameters[VM_USERNAME_INDEX],
                                            "The virtual machine username must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[VM_PASSWORD_INDEX],
                                            "The virtual machine password must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[NUMBER_OF_INSTANCES_INDEX],
                                            "The number of instances to create must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[NUMBER_OF_NODES_PER_INSTANCE_INDEX],
                                            "The number of nodes per instance to deploy must be specified");
        if (parameters[DOWNLOAD_COMMAND_INDEX] == null || getParameter(parameters, DOWNLOAD_COMMAND_INDEX).isEmpty()) {
            parameters[DOWNLOAD_COMMAND_INDEX] = generateDefaultDownloadCommand((String) parameters[IMAGE_OS_TYPE_INDEX],
                                                                                (String) parameters[RM_HTTP_URL_INDEX]);
        }
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

        // this is going to terminate the infrastructure and to restart it,
        // by reinitializing the rest paths. Even in case of RM recovery, it
        // is safer to proceed this way since we don't know if the connector
        // IAAS was standalone or running as part of the scheduler. We must
        // make sure that the REST paths are set up.
        connectorIaasController.createAzureInfrastructure(getInfrastructureId(),
                                                          clientId,
                                                          secret,
                                                          domain,
                                                          subscriptionId,
                                                          authenticationEndpoint,
                                                          managementEndpoint,
                                                          resourceManagerEndpoint,
                                                          graphEndpoint,
                                                          RM_CLOUD_INFRASTRUCTURES_DESTROY_INSTANCES_ON_SHUTDOWN.getValueAsBoolean());

        String instanceTag = getInfrastructureId();
        Set<String> instancesIds;
        boolean existPersistedInstanceIds = false;

        if (expectInstancesAlreadyCreated(false, true)) {
            // it is a fresh deployment: create or retrieve all instances
            instancesIds = connectorIaasController.createAzureInstances(getInfrastructureId(),
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
                                                                        staticPublicIP);
            LOGGER.info("Instances ids created or retrieved : " + instancesIds);
        } else {
            // if the infrastructure was already created, then wee need to
            // look at the free instances, if any (the ones on which no node
            // run. In the current implementation, this can only happen when
            // nodes are down. Indeed if they are all removed on purpose, the
            // instance should be shut down). Note that in this case, if the
            // free instances map is empty, no script will be run at all.
            Map<String, Integer> freeInstancesMap = getInstancesWithoutNodesMapCopy();
            instancesIds = freeInstancesMap.keySet();
            LOGGER.info("Instances ids previously saved which require script re-execution: " + instancesIds);
            existPersistedInstanceIds = true;
        }

        // execute script on instances to deploy or redeploy nodes on them
        for (String currentInstanceId : instancesIds) {
            List<String> scripts = linuxInitScriptGenerator.buildScript(currentInstanceId,
                                                                        getRmUrl(),
                                                                        rmHttpUrl,
                                                                        instanceIdNodeProperty,
                                                                        additionalProperties,
                                                                        nodeSource.getName(),
                                                                        currentInstanceId,
                                                                        numberOfNodesPerInstance);
            try {
                connectorIaasController.executeScript(getInfrastructureId(), currentInstanceId, scripts);
            } catch (ScriptNotExecutedException exception) {
                boolean acquireNodeTriggered = handleScriptNotExecutedException(existPersistedInstanceIds,
                                                                                currentInstanceId,
                                                                                exception);
                if (acquireNodeTriggered) {
                    // in this case we re-attempted a deployment, so we need
                    // to stop looping
                    break;
                }
            } finally {
                // in all cases, we must remove the instance from the free
                // instance map as we tried everything to deploy nodes on it
                removeFromInstancesWithoutNodesMap(currentInstanceId);
            }
        }

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

        unregisterNodeAndRemoveInstanceIfNeeded(instanceId,
                                                node.getNodeInformation().getName(),
                                                getInfrastructureId(),
                                                true);
    }

    @Override
    public void notifyAcquiredNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        addNewNodeForInstance(instanceId, node.getNodeInformation().getName());
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

    private String generateDefaultHttpRMUrl() {
        try {
            // best effort, may not work for all machines
            return "http://" + InetAddress.getLocalHost().getCanonicalHostName() + ":8080";
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to retrieve local canonical hostname with error: " + e);
            return DEFAULT_HTTP_RM_URL;
        }
    }

    private String generateScriptFromInstanceId(String instanceId, String osType) {
        // if the script didn't change compared to the previous update,
        // then the script will not be run by the Azure provider.
        // Moreover, the 'forceUpdateTag' is not available in the Azure
        // Java SDK. So in order to make sure that the script slightly
        // changes, we make the scripts begin with a command that prints
        // a new ID each time a script needs to be run after the first
        // executed script.

        UUID scriptExecutionId = UUID.randomUUID();
        String uniqueCommandPart = "echo script-ID" + "; " + "echo " + scriptExecutionId + "; ";

        String startNodeCommand = generateStartNodeCommand(instanceId);
        if (osType.equals(WINDOWS)) {
            String[] splittedStartNodeCommand = startNodeCommand.split(" ");
            StringBuilder windowsDownloadCommand = new StringBuilder("powershell -command \"" + uniqueCommandPart +
                                                                     downloadCommand + "; Start-Process -NoNewWindow " +
                                                                     "'" + startNodeCommand.split(" ")[0] + "'" +
                                                                     " -ArgumentList ");
            for (int i = 1; i < splittedStartNodeCommand.length; i++) {
                windowsDownloadCommand.append("'").append(splittedStartNodeCommand[i]).append("'");
                if (i < (splittedStartNodeCommand.length - 1)) {
                    windowsDownloadCommand.append(", ");
                }
            }
            windowsDownloadCommand.append("\"");
            return windowsDownloadCommand.toString();
        } else {
            return "/bin/bash -c '" + uniqueCommandPart + downloadCommand + "; nohup " + startNodeCommand + " &'";
        }
    }

    private String generateDefaultDownloadCommand(String osType, String rmHostname) {
        if (osType.equals(WINDOWS)) {
            return POWERSHELL_DOWNLOAD_CMD.replace(RM_HTTP_URL_PATTERN, rmHostname);
        } else {
            return WGET_DOWNLOAD_CMD.replace(RM_HTTP_URL_PATTERN, rmHostname);
        }
    }

    private String generateStartNodeCommand(String instanceId) {
        try {
            String rmHostname = new URL(rmHttpUrl).getHost();
            return START_NODE_CMD.replace(RM_HTTP_URL_PATTERN, rmHostname)
                                 .replace(INSTANCE_ID_PATTERN, instanceId)
                                 .replace(ADDITIONAL_PROPERTIES_PATTERN, additionalProperties)
                                 .replace(RM_URL_PATTERN, getRmUrl())
                                 .replace(NODESOURCE_NAME_PATTERN, nodeSource.getName())
                                 .replace(NUMBER_OF_NODES_PATTERN, String.valueOf(numberOfNodesPerInstance));
        } catch (Exception e) {
            LOGGER.error("Exception when generating the command, fallback on default value", e);
            return START_NODE_FALLBACK_CMD.replace(INSTANCE_ID_PATTERN, instanceId)
                                          .replace(ADDITIONAL_PROPERTIES_PATTERN, additionalProperties)
                                          .replace(RM_URL_PATTERN, getRmUrl())
                                          .replace(NODESOURCE_NAME_PATTERN, nodeSource.getName())
                                          .replace(NUMBER_OF_NODES_PATTERN, String.valueOf(numberOfNodesPerInstance));
        }
    }

    @Override
    public Map<Integer, String> getSectionDescriptions() {
        Map<Integer, String> sectionDescriptions = super.getSectionDescriptions();
        sectionDescriptions.put(1, "Azure Configuration");
        sectionDescriptions.put(2, "Endpoints");
        sectionDescriptions.put(3, "PA Server Configuration");
        sectionDescriptions.put(4, "Deployment Configuration");
        sectionDescriptions.put(5, "VM Configuration");
        sectionDescriptions.put(6, "Network Configuration");
        sectionDescriptions.put(7, "Node Configuration");
        return sectionDescriptions;
    }
}
