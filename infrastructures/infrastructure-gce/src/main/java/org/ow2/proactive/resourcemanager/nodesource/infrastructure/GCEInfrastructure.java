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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.model.NodeConfiguration;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.model.VmCredentials;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.InitScriptGenerator;
import org.ow2.proactive.resourcemanager.rmnode.RMDeployingNode;
import org.ow2.proactive.resourcemanager.utils.RMNodeStarter;
import org.scijava.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;


public class GCEInfrastructure extends AbstractAddonInfrastructure {

    public static final String INFRASTRUCTURE_TYPE = "google-compute-engine";

    public static final String DEFAULT_MACHINE_TYPE = "";

    public static final int DEFAULT_RAM = 4096;

    public static final int DEFAULT_CORES = 2;

    @Getter
    private final String instanceIdNodeProperty = "instanceTag";

    private static final Logger logger = Logger.getLogger(GCEInfrastructure.class);

    private static final String DEFAULT_IMAGE = "debian-9-stretch-v20210916";

    private static final String DEFAULT_REGION = "europe-west2-c";

    private static final int DEFAULT_NODE_TIMEOUT = 10 * 60 * 1000;// 10 min

    private static final boolean DESTROY_INSTANCES_ON_SHUTDOWN = true;

    // the initial scripts to be executed on each node requires the identification of the instance (i.e., instanceTag), which can be retrieved through its hostname on each instance.
    private static final String INSTANCE_TAG_ON_NODE = "$HOSTNAME";

    // use the instanceTag as the nodeName
    private static final String NODE_NAME_ON_NODE = "$HOSTNAME";

    private static final String GCE_API_DOMAIN = "https://www.googleapis.com/compute/v1/projects";

    private transient InitScriptGenerator initScriptGenerator = new InitScriptGenerator();

    // The lock is used to limit the impact of a jclouds bug (When the google-compute-engine account has any deleting instance,
    // any jclouds gce instances operations will fail).
    private static ReadWriteLock deletingLock = new ReentrantReadWriteLock();

    // wrap the read access to deletingLock, used when performing any jclouds gce instances operations other than deleting
    private static Lock readDeletingLock = deletingLock.readLock();

    // wrap the write access to deletingLock, used when performing deleting operation
    private static Lock writeDeletingLock = deletingLock.writeLock();

    // Lock for acquireNodes (dynamic policy)
    private final transient Lock dynamicAcquireLock = new ReentrantLock();

    private boolean isCreatedInfrastructure = false;

    // The index of the infrastructure configurable parameters.
    protected enum Indexes {
        GCE_CREDENTIAL(0),
        TOTAL_NUMBER_OF_INSTANCES(1),
        NUMBER_OF_NODES_PER_INSTANCE(2),
        VM_USERNAME(3),
        VM_PUBLIC_KEY(4),
        VM_PRIVATE_KEY(5),
        IMAGE(6),
        REGION(7),
        MACHINE_TYPE(8),
        RAM(9),
        CORES(10),
        RM_HOSTNAME(11),
        CONNECTOR_IAAS_URL(12),
        NODE_JAR_URL(13),
        ADDITIONAL_PROPERTIES(14),
        NODE_TIMEOUT(15),
        STARTUP_SCRIPT(16);

        protected int index;

        Indexes(int index) {
            this.index = index;
        }
    }

    @Configurable(fileBrowser = true, description = "The JSON key file path of your Google Cloud Platform service account", sectionSelector = 1, important = true)
    protected GCECredential gceCredential = null;

    @Configurable(description = "Total instances to create (maximum number of instances in case of dynamic policy)", sectionSelector = 2, important = true)
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance", sectionSelector = 2, important = true)
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "The virtual machine username (optional)", sectionSelector = 3)
    protected String vmUsername = null;

    @Configurable(fileBrowser = true, description = "The public key for accessing the virtual machine (optional)", sectionSelector = 3)
    protected String vmPublicKey = null;

    @Configurable(fileBrowser = true, description = "The private key for accessing the virtual machine (optional)", sectionSelector = 3)
    protected String vmPrivateKey = null;

    @Configurable(description = "The image of the virtual machine (optional, default value: " + DEFAULT_IMAGE +
                                ")", sectionSelector = 3, important = true)
    protected String image = DEFAULT_IMAGE;

    @Configurable(description = "The region of the virtual machine (optional, default value: " + DEFAULT_REGION +
                                ")", sectionSelector = 3, important = true)
    protected String region = DEFAULT_REGION;

    @Configurable(description = "The machine type required for each VM (optional, e.g. c2d-highcpu-2). Once this parameter is set it will override the parameters set for ram and cores", sectionSelector = 3, important = true)
    protected String machineType = DEFAULT_MACHINE_TYPE;

    @Configurable(description = "The minimum RAM required (in Mega Bytes) for each virtual machine (optional, default value: " +
                                DEFAULT_RAM + ")", sectionSelector = 3, important = true)
    protected int ram = DEFAULT_RAM;

    @Configurable(description = "The minimum number of CPU cores required for each virtual machine (optional, default value: " +
                                DEFAULT_CORES + ")", sectionSelector = 3, important = true)
    protected int cores = DEFAULT_CORES;

    @Configurable(description = "Resource manager hostname or ip address (must be accessible from nodes)", sectionSelector = 4)
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL", sectionSelector = 4)
    protected String connectorIaasURL = InitScriptGenerator.generateDefaultIaasConnectorURL(generateDefaultRMHostname());

    @Configurable(description = "URL used to download the node jar on the virtual machine", sectionSelector = 4)
    protected String nodeJarURL = InitScriptGenerator.generateDefaultNodeJarURL(generateDefaultRMHostname());

    @Configurable(textAreaOneLine = true, description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\") (optional)", sectionSelector = 5)
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    @Configurable(description = "Node timeout in ms. After this timeout expired, the node is considered to be lost (optional, default value: " +
                                DEFAULT_NODE_TIMEOUT + ")", sectionSelector = 5)
    protected int nodeTimeout = DEFAULT_NODE_TIMEOUT;

    @Configurable(textArea = true, description = "VM startup script to launch the ProActive nodes (optional). Please refer to the documentation for full description.", sectionSelector = 5)
    protected String startupScript = initScriptGenerator.getDefaultLinuxStartupScript();

    private Map<String, String> meta = new HashMap<>();

    {
        meta.putAll(super.getMeta());
        meta.put(InfrastructureManager.ELASTIC, "true");
    }

    @Override
    public void configure(Object... parameters) {
        logger.info("Validating parameters : " + Arrays.toString(parameters));
        if (parameters == null || parameters.length < Indexes.values().length) {
            throw new IllegalArgumentException("Invalid parameters for GCEInfrastructure creation");
        }

        this.gceCredential = getCredentialFromJsonKeyFile(parseMandatoryFileParameter("gceCredential",
                                                                                      parameters[Indexes.GCE_CREDENTIAL.index]));
        this.numberOfInstances = parseIntParameter("totalNumberOfInstances",
                                                   parameters[Indexes.TOTAL_NUMBER_OF_INSTANCES.index]);
        this.numberOfNodesPerInstance = parseIntParameter("numberOfNodesPerInstance",
                                                          parameters[Indexes.NUMBER_OF_NODES_PER_INSTANCE.index]);
        this.vmUsername = parseOptionalParameter(parameters[Indexes.VM_USERNAME.index]);
        this.vmPublicKey = parseFileParameter("vmPublicKey", parameters[Indexes.VM_PUBLIC_KEY.index]);
        this.vmPrivateKey = parseFileParameter("vmPrivateKey", parameters[Indexes.VM_PRIVATE_KEY.index]);
        this.image = parseOptionalParameter(parameters[Indexes.IMAGE.index], DEFAULT_IMAGE);
        this.region = parseOptionalParameter(parameters[Indexes.REGION.index], DEFAULT_REGION);
        this.machineType = parseOptionalParameter(parameters[Indexes.MACHINE_TYPE.index], DEFAULT_MACHINE_TYPE);
        this.ram = parseIntParameter("ram", parameters[Indexes.RAM.index], DEFAULT_RAM);
        this.cores = parseIntParameter("cores", parameters[Indexes.CORES.index], DEFAULT_CORES);
        this.rmHostname = parseHostnameParameter("rmHostname", parameters[Indexes.RM_HOSTNAME.index]);
        this.connectorIaasURL = parseMandatoryParameter("connectorIaasURL",
                                                        parameters[Indexes.CONNECTOR_IAAS_URL.index]);
        this.nodeJarURL = parseMandatoryParameter("nodeJarURL", parameters[Indexes.NODE_JAR_URL.index]);
        this.additionalProperties = parseOptionalParameter(parameters[Indexes.ADDITIONAL_PROPERTIES.index]);
        this.nodeTimeout = parseIntParameter("nodeTimeout",
                                             parameters[Indexes.NODE_TIMEOUT.index],
                                             DEFAULT_NODE_TIMEOUT);
        this.startupScript = parseOptionalParameter(parameters[Indexes.STARTUP_SCRIPT.index],
                                                    initScriptGenerator.getDefaultLinuxStartupScript());
        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    private GCECredential getCredentialFromJsonKeyFile(String gceCreds) {
        try {
            final JsonObject json = new JsonParser().parse(gceCreds).getAsJsonObject();
            String clientEmail = json.get("client_email").toString().trim().replace("\"", "");
            String privateKey = json.get("private_key").toString().replace("\"", "").replace("\\n", "\n");
            String projectId = json.get("project_id").toString().trim().replace("\"", "");
            return new GCECredential(clientEmail, privateKey, projectId);
        } catch (Exception e) {
            logger.error(e);
            throw new IllegalArgumentException("Can't parse the GCE service account JSON key file: " + gceCreds);
        }
    }

    @Override
    public void acquireAllNodes() {
        nodeSource.executeInParallel(() -> {
            deployInstancesWithNodes(numberOfInstances, true);
        });
    }

    @Override
    public void acquireNode() {
        nodeSource.executeInParallel(() -> {
            deployInstancesWithNodes(1, true);
        });
    }

    @Override
    public synchronized void acquireNodes(final int numberOfNodes, final long startTimeout,
            final Map<String, ?> nodeConfiguration) {
        nodeSource.executeInParallel(() -> {
            try {
                if (dynamicAcquireLock.tryLock(startTimeout, TimeUnit.MILLISECONDS)) {
                    logger.info(String.format("Acquiring %d nodes with the configuration: %s.",
                                              numberOfNodes,
                                              nodeConfiguration));
                    try {
                        int nbInstancesToDeploy = calNumberOfInstancesToDeploy(numberOfNodes,
                                                                               nodeConfiguration,
                                                                               numberOfInstances,
                                                                               numberOfNodesPerInstance);
                        if (nbInstancesToDeploy <= 0) {
                            logger.info("No need to deploy new instances, acquireNodes skipped.");
                            return;
                        }
                        GCECustomizableParameter deployParams = getNodeSpecificParameters(nodeConfiguration);
                        deployInstancesWithNodes(nbInstancesToDeploy, false, deployParams);
                    } catch (Exception e) {
                        logger.error("Error during node acquisition", e);
                    } finally {
                        dynamicAcquireLock.unlock();
                    }
                } else {
                    logger.info("Infrastructure is busy, acquireNodes skipped.");
                }
            } catch (InterruptedException e) {
                logger.info("acquireNodes skipped because of InterruptedException:", e);
            }
        });
    }

    private void deployInstancesWithNodes(int nbInstancesToDeploy, boolean reuseCreatedInstances) {
        deployInstancesWithNodes(nbInstancesToDeploy, reuseCreatedInstances, getDefaultNodeParameters());
    }

    /**
     * deploy {@code nbInstancesToDeploy} instances with  {@code numberOfNodesPerInstance} nodes on each instance
     * @param nbInstancesToDeploy number of instances to deploy
     */
    private void deployInstancesWithNodes(int nbInstancesToDeploy, boolean reuseCreatedInstances,
            GCECustomizableParameter params) {
        logger.info(String.format("Deploying %d instances with %d nodes on each instance.",
                                  nbInstancesToDeploy,
                                  numberOfNodesPerInstance));

        connectorIaasController.waitForConnectorIaasToBeUP();

        String infrastructureId = getInfrastructureId();

        List<String> nodeStartCmds = buildNodeStartScripts(numberOfNodesPerInstance);

        logger.info("start up script: " + nodeStartCmds);

        Set<String> instancesIds;

        readDeletingLock.lock();
        try {
            createGceInfrastructureIfNeeded(infrastructureId);

            instancesIds = createInstanceWithNodesStartCmd(infrastructureId, nbInstancesToDeploy, nodeStartCmds);
        } finally {
            readDeletingLock.unlock();
        }

        declareDeployingNodes(instancesIds, numberOfNodesPerInstance, nodeStartCmds.toString());
    }

    private void createGceInfrastructureIfNeeded(String infrastructureId) {
        // Create infrastructure if it does not exist
        if (!isCreatedInfrastructure) {
            connectorIaasController.createInfrastructure(infrastructureId,
                                                         gceCredential.clientEmail,
                                                         gceCredential.privateKey,
                                                         null,
                                                         DESTROY_INSTANCES_ON_SHUTDOWN);
            isCreatedInfrastructure = true;
        }
    }

    private List<String> buildNodeStartScripts(int numberOfNodes) {
        try {
            return initScriptGenerator.buildLinuxScript(startupScript,
                                                        INSTANCE_TAG_ON_NODE,
                                                        getRmUrl(),
                                                        rmHostname,
                                                        nodeJarURL,
                                                        instanceIdNodeProperty,
                                                        additionalProperties,
                                                        nodeSource.getName(),
                                                        NODE_NAME_ON_NODE,
                                                        numberOfNodes,
                                                        getCredentials());
        } catch (KeyException a) {
            logger.error("A problem occurred while acquiring user credentials path. The node startup script will be empty.");
            return new ArrayList<>();
        }
    }

    private Set<String> createInstanceWithNodesStartCmd(String infrastructureId, int nbInstances,
            List<String> initScripts) {

        return connectorIaasController.createGCEInstances(infrastructureId,
                                                          infrastructureId,
                                                          nbInstances,
                                                          vmUsername,
                                                          vmPublicKey,
                                                          vmPrivateKey,
                                                          initScripts,
                                                          image,
                                                          region,
                                                          createMachineTypeUrl(),
                                                          ram,
                                                          cores);
    }

    private void declareDeployingNodes(Set<String> instancesIds, int nbNodesPerInstance, String nodeStartCmd) {
        List<String> nodeNames = new ArrayList<>();
        for (String instanceId : instancesIds) {
            String instanceTag = stringAfterLastSlash(instanceId);
            nodeNames.addAll(RMNodeStarter.getWorkersNodeNames(instanceTag, nbNodesPerInstance));
        }
        // declare nodes as "deploying"
        Executors.newCachedThreadPool().submit(() -> {
            List<String> deployingNodes = addMultipleDeployingNodes(nodeNames,
                                                                    nodeStartCmd,
                                                                    "Node deployment on Google Compute Engine",
                                                                    nodeTimeout);
            logger.info("Deploying nodes: " + deployingNodes);
        });
    }

    @Override
    public void notifyAcquiredNode(Node node) throws RMException {
        String instanceTag = getInstanceIdProperty(node);

        addNewNodeForInstance(instanceTag, node.getNodeInformation().getName());
    }

    @Override
    protected void notifyDeployingNodeLost(String pnURL) {
        super.notifyDeployingNodeLost(pnURL);
        logger.info("Unregistering the lost node " + pnURL);
        RMDeployingNode currentNode = getDeployingOrLostNode(pnURL);
        String instanceTag = parseInstanceTagFromNodeName(currentNode.getNodeName());

        // Delete the instance when instance doesn't contain any other deploying nodes or persisted nodes
        if (!existOtherDeployingNodesOnInstance(currentNode, instanceTag) &&
            !existRegisteredNodesOnInstance(instanceTag)) {
            terminateInstance(getInfrastructureId(), instanceTag);
        }
    }

    private boolean existOtherDeployingNodesOnInstance(RMDeployingNode currentNode, String instanceTag) {
        for (RMDeployingNode node : getDeployingAndLostNodes()) {
            if (!node.equals(currentNode) && !node.isLost() &&
                parseInstanceTagFromNodeName(node.getNodeName()).equals(instanceTag)) {
                return true;
            }
        }
        return false;
    }

    private void terminateInstance(String infrastructureId, String instanceTag) {
        nodeSource.executeInParallel(() -> {
            writeDeletingLock.lock();
            try {
                connectorIaasController.terminateInstanceByTag(infrastructureId, instanceTag);
                logger.info("Terminated the instance: " + instanceTag);
            } finally {
                writeDeletingLock.unlock();
            }
        });
    }

    @Override
    public void removeNode(Node node) throws RMException {
        String nodeName = node.getNodeInformation().getName();
        String instanceId = getInstanceIdProperty(node);
        try {
            node.getProActiveRuntime().killNode(nodeName);
        } catch (Exception e) {
            logger.warn("Unable to remove the node: " + nodeName, e);
        }
        unregisterNodeAndRemoveInstanceIfNeeded(instanceId, nodeName, getInfrastructureId(), true);
    }

    @Override
    public void shutDown() {
        super.shutDown();
        String infrastructureId = getInfrastructureId();
        writeDeletingLock.lock();
        try {
            logger.info(String.format("Deleting infrastructure (%s) and its instances", infrastructureId));
            connectorIaasController.terminateInfrastructure(infrastructureId, true);
            logger.info(String.format("Successfully deleted infrastructure (%s) and its instances.", infrastructureId));
        } finally {
            writeDeletingLock.unlock();
        }

    }

    @Override
    public String getDescription() {
        return "Handles nodes from the Google Compute Engine.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("GCEInfrastructure numberOfNodesPerInstance: [%s], totalNumberOfInstances: [%s], region: [%s]",
                             numberOfNodesPerInstance,
                             numberOfInstances,
                             region);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void unregisterNodeAndRemoveInstanceIfNeeded(final String instanceTag, final String nodeName,
            final String infrastructureId, final boolean terminateInstanceIfEmpty) {
        setPersistedInfraVariable(() -> {
            // first read from the runtime variables map
            nodesPerInstance = (Map<String, Set<String>>) persistedInfraVariables.get(NODES_PER_INSTANCES_KEY);
            // make modifications to the nodesPerInstance map
            if (nodesPerInstance.get(instanceTag) != null) {
                nodesPerInstance.get(instanceTag).remove(nodeName);
                logger.info("Removed node: " + nodeName);
                if (nodesPerInstance.get(instanceTag).isEmpty()) {
                    if (terminateInstanceIfEmpty) {
                        terminateInstance(infrastructureId, instanceTag);
                    }
                    nodesPerInstance.remove(instanceTag);
                    logger.info("Removed instance: " + instanceTag);
                }
                // finally write to the runtime variable map
                decrementNumberOfAcquiredNodesWithLockAndPersist();
                persistedInfraVariables.put(NODES_PER_INSTANCES_KEY, Maps.newHashMap(nodesPerInstance));
            } else {
                logger.error("Cannot remove node " + nodeName + " because instance " + instanceTag +
                             " is not registered");
            }
            return null;
        });
    }

    /**
     * Get the sub-string after the last slash in 'completeString'.
     * It is used to :
     * - parse the GCE instance tag (e.g., gce-afa) from instance id (e.g., https://www.googleapis.com/compute/v1/projects/fifth-totality-235316/zones/us-central1-a/instances/gce-afa)
     * - parse the node name (e.g., instance-node_0) from deploying node URL (e.g., deploying://infra/instance-node_0)
     *
     * @param completeString the complete string to parse
     * @return substring after last slash
     */
    private static String stringAfterLastSlash(String completeString) {
        return completeString.substring(completeString.lastIndexOf('/') + 1);
    }

    /**
     * Parse the instanceTag (i.e., baseNodeName) from the complete nodeName.
     * The nodeName may contain an index (e.g., _0, _1) as suffix or not.
     * @param nodeName (e.g., instance-node_0, instance-node_1, or instance-node)
     * @return instanceTag (e.g., instance-node)
     */
    private static String parseInstanceTagFromNodeName(String nodeName) {
        int indexSeparator = nodeName.lastIndexOf('_');
        if (indexSeparator == -1) {
            // when nodeName contains no indexSeparator, instanceTag is same as nodeName
            return nodeName;
        } else {
            return nodeName.substring(0, indexSeparator);
        }
    }

    @Getter
    @AllArgsConstructor
    @ToString
    class GCECredential {
        String clientEmail;

        String privateKey;

        String projectId;
    }

    @Override
    public Map<Integer, String> getSectionDescriptions() {
        Map<Integer, String> sectionDescriptions = super.getSectionDescriptions();
        sectionDescriptions.put(1, "GCE Configuration");
        sectionDescriptions.put(2, "Deployment Configuration");
        sectionDescriptions.put(3, "VM Configuration");
        sectionDescriptions.put(4, "PA Server Configuration");
        sectionDescriptions.put(5, "Node Configuration");
        sectionDescriptions.put(6, "Startup Scripts");
        return sectionDescriptions;
    }

    @Override
    public Map<String, String> getMeta() {
        return meta;
    }

    private GCECustomizableParameter getDefaultNodeParameters() {
        return new GCECustomizableParameter(image,
                                            vmUsername,
                                            vmPublicKey,
                                            vmPrivateKey,
                                            region,
                                            machineType,
                                            ram,
                                            cores,
                                            additionalProperties);
    }

    // get the node deployment parameters based on the specific node configurations which can
    // overrides the values specified in the infrastructure configuration
    private GCECustomizableParameter getNodeSpecificParameters(Map<String, ?> nodeConfiguration) {
        GCECustomizableParameter params = getDefaultNodeParameters();
        NodeConfiguration nodeConfig = new ObjectMapper().convertValue(nodeConfiguration, NodeConfiguration.class);

        if (nodeConfig.getImage() != null) {
            params.setImage(nodeConfig.getImage());
        }
        if (nodeConfig.getCredentials() != null) {
            VmCredentials vmCred = nodeConfig.getCredentials();
            if (vmCred.getVmUserName() != null) {
                params.setVmUsername(vmCred.getVmUserName());
            }
            if (vmCred.getVmPublicKey() != null) {
                params.setVmPublicKey(vmCred.getVmPublicKey());
            }
            if (vmCred.getVmPrivateKey() != null) {
                params.setVmPrivateKey(vmCred.getVmPrivateKey());
            }
        }
        if (nodeConfig.getRegion() != null) {
            params.setRegion(nodeConfig.getRegion());
        }
        if (nodeConfig.getMachineType() != null) {
            params.setMachineType(nodeConfig.getMachineType());
        }
        if (nodeConfig.getAmountOfMemory() != null) {
            params.setRam(nodeConfig.getAmountOfMemory());
        }
        if (nodeConfig.getNumberOfCores() != null) {
            params.setCores(nodeConfig.getNumberOfCores());
        }
        if (nodeConfig.getNodeTags() != null) {
            params.setAdditionalProperties(addTagsInJvmAdditionalProperties(params.getAdditionalProperties(),
                                                                            nodeConfig.getNodeTags()));
        }

        return params;
    }

    private boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    private String createMachineTypeUrl() {
        if (!StringUtils.isNullOrEmpty(machineType)) {
            if (isValidURL(machineType)) {
                logger.info("The machineType selected based on the infrastructure definition is: " + machineType);
                return machineType;
            } else {
                String machineTypeUrl = String.format("%s/%s/zones/%s/machineTypes/%s",
                                                      GCE_API_DOMAIN,
                                                      gceCredential.projectId,
                                                      region,
                                                      machineType);
                logger.info("The machineType selected based on the infrastructure definition is: " + machineTypeUrl);
                return machineTypeUrl;
            }
        }
        return "";
    }
}
