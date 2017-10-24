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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;


public class MaasInfrastructure extends InfrastructureManager {

    private static final Logger LOGGER = Logger.getLogger(MaasInfrastructure.class);

    public static final String INSTANCE_ID_NODE_PROPERTY = "instanceId";

    public static final String INFRASTRUCTURE_TYPE = "maas";

    private static final String DEFAULT_RM_HOSTNAME = "localhost";

    private final static int PARAMETERS_NUMBER = 13;

    // Indexes of parameters
    private final static int API_TOKEN_INDEX = 0;

    private final static int ENDPOINT_INDEX = 1;

    private final static int RM_HOSTNAME_INDEX = 2;

    private final static int CONNECTOR_IAAS_URL_INDEX = 3;

    private final static int IMAGE_INDEX = 4;

    private final static int SYSTEM_ID_INDEX = 5;

    private final static int MIN_CPU_INDEX = 6;

    private final static int MIN_MEM_INDEX = 7;

    private final static int NUMBER_OF_INSTANCES_INDEX = 8;

    private final static int NUMBER_OF_NODES_PER_INSTANCE_INDEX = 9;

    private final static int DOWNLOAD_COMMAND_INDEX = 10;

    private final static int IGNORE_CERTIFICATE_CHECK_INDEX = 11;

    private final static int ADDITIONAL_PROPERTIES_INDEX = 12;

    // Command lines patterns
    private static final CharSequence RM_HOSTNAME_PATTERN = "<RM_HOSTNAME>";

    private static final CharSequence RM_URL_PATTERN = "<RM_URL>";

    private static final CharSequence COMMUNICATION_PROTOCOL_PATTERN = "<COMMUNICATION_PROTOCOL>";

    private static final CharSequence INSTANCE_ID_PATTERN = "<INSTANCE_ID>";

    private static final CharSequence ADDITIONAL_PROPERTIES_PATTERN = "<ADDITIONAL_PROPERTIES>";

    private static final CharSequence NODESOURCE_NAME_PATTERN = "<NODESOURCE_NAME>";

    private static final CharSequence NUMBER_OF_NODES_PATTERN = "<NUMBER_OF_NODES>";

    // Command lines definition
    private static final String POWERSHELL_DOWNLOAD_CMD = "powershell -command \"& { (New-Object Net.WebClient).DownloadFile('http://" +
                                                          RM_HOSTNAME_PATTERN + ":8080/rest/node.jar" +
                                                          "', 'node.jar') }\"";

    private static final String WGET_DOWNLOAD_CMD = "wget -nv http://" + RM_HOSTNAME_PATTERN + ":8080/rest/node.jar";

    private static final String START_NODE_CMD = "java -jar node.jar -Dproactive.communication.protocol=" +
                                                 COMMUNICATION_PROTOCOL_PATTERN + " -Dproactive.pamr.router.address=" +
                                                 RM_HOSTNAME_PATTERN + " -D" + INSTANCE_ID_NODE_PROPERTY + "=" +
                                                 INSTANCE_ID_PATTERN + " " + ADDITIONAL_PROPERTIES_PATTERN + " -r " +
                                                 RM_URL_PATTERN + " -s " + NODESOURCE_NAME_PATTERN + " -w " +
                                                 NUMBER_OF_NODES_PATTERN;

    private static final String START_NODE_FALLBACK_CMD = "java -jar node.jar -D" + INSTANCE_ID_NODE_PROPERTY + "=" +
                                                          INSTANCE_ID_PATTERN + " " + ADDITIONAL_PROPERTIES_PATTERN +
                                                          " -r " + RM_URL_PATTERN + " -s " + NODESOURCE_NAME_PATTERN +
                                                          " -w " + NUMBER_OF_NODES_PATTERN;

    @Configurable(description = "The MAAS API token")
    protected String apiToken = null;

    @Configurable(description = "The MAAS endpoint")
    protected String endpoint = null;

    @Configurable(description = "Resource manager hostname or ip address")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = "http://" + generateDefaultRMHostname() + ":8080/connector-iaas";

    @Configurable(description = "Image to deploy (if not specify the default image of the platform will be used)")
    protected String image = null;

    @Configurable(description = "System ID of the Machine to deploy (if set, minimal resources constraints below will be ignored and only a single machine will be deployed)")
    protected String systemId = null;

    @Configurable(description = "Minimal amount of CPU")
    protected String vmMinCpu = null;

    @Configurable(description = "Minimal amount of Memory (MB)")
    protected String vmMinMem = null;

    @Configurable(description = "Total instance to create")
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Command used to download the worker jar")
    protected String downloadCommand = generateDefaultDownloadCommand();

    @Configurable(description = "Optional flag to specify if untrusted (eg. self-signed) certificate are allowed")
    protected boolean ignoreCertificateCheck = false;

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    //protected String additionalProperties = "-Dproactive.useIPaddress=true -Dproactive.net.public_address=$(wget -qO- ipinfo.io/ip) -Dproactive.communication.protocol=pnp -Dproactive.pnp.port=64738 -Dproactive.pamr.router.port=";
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    protected ConnectorIaasController connectorIaasController = null;

    protected final Map<String, Set<String>> nodesPerInstances;

    /**
     * Default constructor
     */
    public MaasInfrastructure() {
        nodesPerInstances = Maps.newConcurrentMap();
    }

    @Override
    public void configure(Object... parameters) {

        LOGGER.info("Validating parameters : " + Arrays.toString(parameters));
        validate(parameters);

        this.apiToken = getParameter(parameters, API_TOKEN_INDEX);
        this.endpoint = getParameter(parameters, ENDPOINT_INDEX);
        this.rmHostname = getParameter(parameters, RM_HOSTNAME_INDEX);
        this.connectorIaasURL = getParameter(parameters, CONNECTOR_IAAS_URL_INDEX);
        this.image = getParameter(parameters, IMAGE_INDEX);
        this.systemId = getParameter(parameters, SYSTEM_ID_INDEX);
        this.vmMinCpu = getParameter(parameters, MIN_CPU_INDEX);
        this.vmMinMem = getParameter(parameters, MIN_MEM_INDEX);
        this.numberOfInstances = Integer.parseInt(getParameter(parameters, NUMBER_OF_INSTANCES_INDEX));
        this.numberOfNodesPerInstance = Integer.parseInt(getParameter(parameters, NUMBER_OF_NODES_PER_INSTANCE_INDEX));
        this.downloadCommand = getParameter(parameters, DOWNLOAD_COMMAND_INDEX);
        this.ignoreCertificateCheck = Boolean.parseBoolean(getParameter(parameters, IGNORE_CERTIFICATE_CHECK_INDEX));
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

        throwIllegalArgumentExceptionIfNull(parameters[API_TOKEN_INDEX], "MAAS API apiToken must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[ENDPOINT_INDEX], "MAAS endpoint must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[RM_HOSTNAME_INDEX],
                                            "The Resource manager hostname must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[CONNECTOR_IAAS_URL_INDEX],
                                            "The connector-iaas URL must be specified");
        if (parameters[MIN_CPU_INDEX] == null && parameters[MIN_MEM_INDEX] == null) {
            throwIllegalArgumentExceptionIfNull(parameters[SYSTEM_ID_INDEX],
                                                "Wether minimal resources (CPU and/or Memory) or a system ID must be specified.");
        }
        throwIllegalArgumentExceptionIfNull(parameters[NUMBER_OF_INSTANCES_INDEX],
                                            "The number of instances to create must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[NUMBER_OF_NODES_PER_INSTANCE_INDEX],
                                            "The number of nodes per instance to deploy must be specified");
        throwIllegalArgumentExceptionIfNull(parameters[DOWNLOAD_COMMAND_INDEX],
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

        // Create MAAS infrastructure & instances
        connectorIaasController.createMaasInfrastructure(getInfrastructureId(),
                                                         apiToken,
                                                         endpoint,
                                                         ignoreCertificateCheck,
                                                         false);
        String instanceTag = getInfrastructureId();
        Set<String> instancesIds;
        instancesIds = connectorIaasController.createMaasInstances(getInfrastructureId(),
                                                                   instanceTag,
                                                                   image,
                                                                   numberOfInstances,
                                                                   systemId,
                                                                   vmMinCpu,
                                                                   vmMinMem,
                                                                   Lists.newArrayList(generateScriptWithoutInstanceId()));
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

    private String generateScriptWithoutInstanceId() {
        String startNodeCommand = generateStartNodeCommand();
        if (System.getProperty("os.name").contains("Windows")) {
            return this.downloadCommand + "; Start-Process -NoNewWindow " + startNodeCommand;
        } else {
            return "/bin/bash -c '" + this.downloadCommand + "; nohup " + startNodeCommand + " &'";
        }
    }

    private String generateDefaultDownloadCommand() {
        if (System.getProperty("os.name").contains("Windows")) {
            return POWERSHELL_DOWNLOAD_CMD.replace(RM_HOSTNAME_PATTERN, this.rmHostname);
        } else {
            return WGET_DOWNLOAD_CMD.replace(RM_HOSTNAME_PATTERN, this.rmHostname);
        }
    }

    private String generateStartNodeCommand() {
        try {
            String communicationProtocol = rmUrl.split(":")[0];
            String startNodeCommand = START_NODE_CMD.replace(COMMUNICATION_PROTOCOL_PATTERN, communicationProtocol)
                                                    .replace(RM_HOSTNAME_PATTERN, rmHostname)
                                                    .replace(ADDITIONAL_PROPERTIES_PATTERN, additionalProperties)
                                                    .replace(RM_URL_PATTERN, rmUrl)
                                                    .replace(NODESOURCE_NAME_PATTERN, nodeSource.getName())
                                                    .replace(NUMBER_OF_NODES_PATTERN,
                                                             String.valueOf(numberOfNodesPerInstance));
            if (systemId != null && !systemId.isEmpty()) {
                startNodeCommand = startNodeCommand.replace(INSTANCE_ID_PATTERN, systemId);
            }
            return startNodeCommand;

        } catch (Exception e) {
            LOGGER.error("Exception when generating the command, fallback on default value", e);
            String startNodeFallbackCommand = START_NODE_FALLBACK_CMD.replace(ADDITIONAL_PROPERTIES_PATTERN,
                                                                              additionalProperties)
                                                                     .replace(RM_URL_PATTERN, rmUrl)
                                                                     .replace(NODESOURCE_NAME_PATTERN,
                                                                              nodeSource.getName())
                                                                     .replace(NUMBER_OF_NODES_PATTERN,
                                                                              String.valueOf(numberOfNodesPerInstance));
            if (systemId != null && !systemId.isEmpty()) {
                startNodeFallbackCommand = startNodeFallbackCommand.replace(INSTANCE_ID_PATTERN, systemId);
            }
            return startNodeFallbackCommand;
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
