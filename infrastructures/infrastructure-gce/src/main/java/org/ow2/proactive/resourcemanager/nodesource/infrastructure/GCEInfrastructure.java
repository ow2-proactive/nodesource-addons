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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.LinuxInitScriptGenerator;
import org.ow2.proactive.resourcemanager.rmnode.RMDeployingNode;
import org.ow2.proactive.resourcemanager.utils.RMNodeStarter;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;


public class GCEInfrastructure extends AbstractAddonInfrastructure {

    public static final String INFRASTRUCTURE_TYPE = "google-compute-engine";

    public static final String INSTANCE_TAG_NODE_PROPERTY = "instanceTag";

    private static final int NUMBER_OF_PARAMETERS = 15;

    private static final Logger logger = Logger.getLogger(GCEInfrastructure.class);

    private static final String DEFAULT_IMAGE = "debian-9-stretch-v20190326";

    private static final String DEFAULT_REGION = "us-central1-a";

    private static final int DEFAULT_RAM = 1740;

    private static final int DEFAULT_CORES = 1;

    private static final boolean DESTROY_INSTANCES_ON_SHUTDOWN = true;

    // the initial scripts to be executed on each node requires the identification of the instance (i.e., instanceTag), which can be retrieved through its hostname on each instance.
    private static final String INSTANCE_TAG_ON_NODE = "$HOSTNAME";

    // use the instanceTag as the nodeName
    private static final String NODE_NAME_ON_NODE = "$HOSTNAME";

    private transient LinuxInitScriptGenerator linuxInitScriptGenerator = new LinuxInitScriptGenerator();

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

    @Configurable(fileBrowser = true, description = "The JSON key file path of your Google Cloud Platform service account")
    protected GCECredential gceCredential = null;

    @Configurable(description = "Total instances to create (maximum number of instances in case of dynamic policy)")
    protected int totalNumberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "(optional) The virtual machine username")
    protected String vmUsername = null;

    @Configurable(fileBrowser = true, description = "(optional) The public key for accessing the virtual machine")
    protected String vmPublicKey = null;

    @Configurable(fileBrowser = true, description = "(optional) The private key for accessing the virtual machine")
    protected String vmPrivateKey = null;

    @Configurable(description = "(optional) The image of the virtual machine")
    protected String image = DEFAULT_IMAGE;

    @Configurable(description = "(optional) The region of the virtual machine")
    protected String region = DEFAULT_REGION;

    @Configurable(description = "(optional) The minimum RAM required (in Mega Bytes) for each virtual machine")
    protected int ram = DEFAULT_RAM;

    @Configurable(description = "(optional) The minimum number of CPU cores required for each virtual machine")
    protected int cores = DEFAULT_CORES;

    @Configurable(description = "Resource manager hostname or ip address (must be accessible from nodes)")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = linuxInitScriptGenerator.generateDefaultIaasConnectorURL(generateDefaultRMHostname());

    @Configurable(description = "URL used to download the node jar on the virtual machine")
    protected String nodeJarURL = linuxInitScriptGenerator.generateDefaultNodeJarURL(rmHostname);

    @Configurable(description = "(optional) Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    @Configurable(description = "Node timeout in ms. After this timeout expired, the node is considered to be lost")
    protected int nodeTimeout = 2 * 60 * 1000;// 2 min

    @Override
    public void configure(Object... parameters) {
        logger.info("Validating parameters : " + Arrays.toString(parameters));
        validate(parameters);

        int parameterIndex = 0;

        this.gceCredential = getCredentialFromJsonKeyFile((byte[]) parameters[parameterIndex++]);
        this.totalNumberOfInstances = parseIntParameter("totalNumberOfInstances", parameters[parameterIndex++]);
        this.numberOfNodesPerInstance = parseIntParameter("numberOfNodesPerInstance", parameters[parameterIndex++]);
        this.vmUsername = parameters[parameterIndex++].toString().trim();
        this.vmPublicKey = new String((byte[]) parameters[parameterIndex++]);
        this.vmPrivateKey = new String((byte[]) parameters[parameterIndex++]);
        this.image = parameters[parameterIndex++].toString().trim();
        this.region = parameters[parameterIndex++].toString().trim();
        this.ram = parseIntParameter("ram", parameters[parameterIndex++]);
        this.cores = parseIntParameter("cores", parameters[parameterIndex++]);
        this.rmHostname = parameters[parameterIndex++].toString().trim();
        this.connectorIaasURL = parameters[parameterIndex++].toString().trim();
        this.nodeJarURL = parameters[parameterIndex++].toString().trim();
        this.additionalProperties = parameters[parameterIndex++].toString().trim();
        this.nodeTimeout = parseIntParameter("nodeTimeout", parameters[parameterIndex++]);

        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    private void validate(Object[] parameters) {
        if (parameters == null || parameters.length < NUMBER_OF_PARAMETERS) {
            throw new IllegalArgumentException("Invalid parameters for GCEInfrastructure creation");
        }
        int parameterIndex = 0;
        // gceCredential
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The Google Cloud Platform service account must be specified");
        }
        // totalNumberOfInstances
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The number of instances to create must be specified");
        }
        // numberOfNodesPerInstance
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The number of nodes per instance to deploy must be specified");
        }
        // vmUsername
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        // vmPublicKey
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        // vmPrivateKey
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        // image
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = DEFAULT_IMAGE;
        }
        // region
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = DEFAULT_REGION;
        }
        // ram
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = String.valueOf(DEFAULT_RAM);
        }
        // cores
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = String.valueOf(DEFAULT_CORES);
        }
        // rmHostname
        parameterIndex++;
        checkRMHostname(parameters[parameterIndex].toString());
        // connectorIaasURL
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The connector-iaas URL must be specified");
        }
        // nodeJarURL
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The URL for downloading the node jar must be specified");
        }
        // additionalProperties
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        // nodeTimeout
        parameterIndex++;
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The node timeout must be specified");
        }
    }

    private GCECredential getCredentialFromJsonKeyFile(byte[] credsFile) {
        try {
            final JsonObject json = new JsonParser().parse(new String(credsFile)).getAsJsonObject();
            String clientEmail = json.get("client_email").toString().trim().replace("\"", "");
            String privateKey = json.get("private_key").toString().replace("\"", "").replace("\\n", "\n");
            return new GCECredential(clientEmail, privateKey);
        } catch (Exception e) {
            logger.error(e);
            throw new IllegalArgumentException("Can't reading the GCE service account JSON key file: " +
                                               new String(credsFile));
        }
    }

    @Override
    public void acquireNode() {
        deployInstancesWithFullNodes(1);
    }

    @Override
    public void acquireAllNodes() {
        deployInstancesWithFullNodes(totalNumberOfInstances);
    }

    @Override
    public synchronized void acquireNodes(final int numberOfNodes, final Map<String, ?> nodeConfiguration) {
        logger.info(String.format("Acquiring %d nodes with the configuration: %s.", numberOfNodes, nodeConfiguration));

        nodeSource.executeInParallel(() -> {
            if (dynamicAcquireLock.tryLock()) {
                try {
                    int nbInstancesToDeploy = calNumberOfInstancesToDeploy(numberOfNodes,
                                                                           nodeConfiguration,
                                                                           totalNumberOfInstances,
                                                                           numberOfNodesPerInstance);
                    if (nbInstancesToDeploy <= 0) {
                        logger.info("No need to deploy new instances, acquireNodes skipped.");
                        return;
                    }
                    deployInstancesWithFullNodes(nbInstancesToDeploy);
                } catch (Exception e) {
                    logger.error("Error during node acquisition", e);
                } finally {
                    dynamicAcquireLock.unlock();
                }
            } else {
                logger.info("Infrastructure is busy, acquireNodes skipped.");
            }
        });
    }

    /**
     * deploy {@code nbInstancesToDeploy} instances with  {@code numberOfNodesPerInstance} nodes on each instance
     * @param nbInstancesToDeploy number of instances to deploy
     */
    private void deployInstancesWithFullNodes(int nbInstancesToDeploy) {
        logger.info(String.format("Deploying %d instances with %d nodes on each instance.",
                                  nbInstancesToDeploy,
                                  numberOfNodesPerInstance));

        connectorIaasController.waitForConnectorIaasToBeUP();

        String infrastructureId = getInfrastructureId();

        List<String> nodeStartCmds = buildNodeStartScripts(numberOfNodesPerInstance);

        Set<String> instancesIds;

        readDeletingLock.lock();
        try {
            createInfrastructureIfNeeded(infrastructureId);

            instancesIds = createInstanceWithNodesStartCmd(infrastructureId, nbInstancesToDeploy, nodeStartCmds);
        } finally {
            readDeletingLock.unlock();
        }

        declareDeployingNodes(instancesIds, numberOfNodesPerInstance, nodeStartCmds.toString());
    }

    private void createInfrastructureIfNeeded(String infrastructureId) {
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
        return linuxInitScriptGenerator.buildScript(INSTANCE_TAG_ON_NODE,
                                                    getRmUrl(),
                                                    rmHostname,
                                                    nodeJarURL,
                                                    INSTANCE_TAG_NODE_PROPERTY,
                                                    additionalProperties,
                                                    nodeSource.getName(),
                                                    NODE_NAME_ON_NODE,
                                                    numberOfNodes);
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
    protected String getInstanceIdProperty(Node node) throws RMException {
        try {
            return node.getProperty(INSTANCE_TAG_NODE_PROPERTY);
        } catch (ProActiveException e) {
            throw new RMException(e);
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
        return getDescription();
    }

    private String generateDefaultRMHostname() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            logger.warn(e);
            return "localhost";
        }
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
    }
}
