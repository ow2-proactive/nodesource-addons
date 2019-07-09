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
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.LinuxInitScriptGenerator;
import org.ow2.proactive.resourcemanager.rmnode.RMDeployingNode;
import org.ow2.proactive.resourcemanager.utils.RMNodeStarter;

import com.google.common.collect.Maps;

import lombok.Getter;


public class AWSEC2Infrastructure extends AbstractAddonInfrastructure {

    @Getter
    private final String instanceIdNodeProperty = "instanceId";

    public static final String INFRASTRUCTURE_TYPE = "aws-ec2";

    private static final int NUMBER_OF_PARAMETERS = 17;

    private static final String DEFAULT_IMAGE = "eu-west-3/ami-03bca18cb3dc173c9";

    private static final String DEFAULT_VM_USERNAME = "ubuntu";

    private static final int DEFAULT_RAM = 2048;

    private static final int DEFAULT_CORES = 2;

    private static final int DEFAULT_NODE_TIMEOUT = 5 * 60 * 1000;// 5 min

    private static final boolean DESTROY_INSTANCES_ON_SHUTDOWN = true;

    // jClouds use the format "region/instanceIdInsideRegion" as the complete instanceId
    private static final String INSTANCE_ID_REGION_DELIMITER = "/";

    // as INSTANCE_ID_REGION_DELIMITER('/') is invalid in the node name, we use another delimiter to replace INSTANCE_ID_REGION_DELIMITER
    // this delimiter is supposed to not appear in the AWS region.
    private static final String INSTANCE_ID_REGION_DELIMITER_IN_NODENAME = "__";

    private static final char NODE_INDEX_DELIMITER = '_';

    private static final Logger logger = Logger.getLogger(AWSEC2Infrastructure.class);

    private transient LinuxInitScriptGenerator linuxInitScriptGenerator = new LinuxInitScriptGenerator();

    // Lock for acquireNodes (dynamic policy)
    private final transient Lock dynamicAcquireLock = new ReentrantLock();

    private boolean isCreatedInfrastructure = false;

    @Configurable(description = "Your AWS access key ID (e.g., AKIAIOSFODNN7EXAMPLE)")
    protected String awsKey = null;

    @Configurable(description = "Your AWS secret access key (e.g., wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY)")
    protected String awsSecretKey = null;

    @Configurable(description = "The number of VMs to create (maximum number of VMs in case of dynamic policy)")
    protected int numberOfInstances = 1;

    @Configurable(description = "The number of nodes to create on each VM")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "(optional, default value: " + DEFAULT_IMAGE + ") VM image id, format region/imageId")
    protected String image = DEFAULT_IMAGE;

    @Configurable(description = "(optional, default value: " + DEFAULT_VM_USERNAME +
                                ") Default username of your VM image, make sure it's adapted to 'image'")
    protected String vmUsername = DEFAULT_VM_USERNAME;

    @Configurable(description = "(optional) The name of your AWS key pair for accessing VM")
    protected String vmKeyPairName = null;

    @Configurable(fileBrowser = true, description = "(optional) Your AWS private key file corresponding to 'vmKeyPairName' for accessing VM")
    protected byte[] vmPrivateKey;

    @Configurable(description = "(optional) The minimum RAM required (in Mega Bytes) for each VM")
    protected int ram = DEFAULT_RAM;

    @Configurable(description = "(optional) The minimum number of CPU cores required for each VM")
    protected int cores = DEFAULT_CORES;

    // TODO disable to configure the parameter spotPrice for the moment, because we don't yet have a checking mechanism for it now, but it may cause the RM portal blocked (hanging in createInstance).
    //    @Configurable(description = "(optional) The maximum price that you are willing to pay per hour per instance (your bid price)")
    protected String spotPrice = "";

    @Configurable(description = "(optional) The ids(s) of the security group(s) for VMs, spearated by comma in case of multiple ids.")
    protected String securityGroupIds = null;

    @Configurable(description = "(optional) The subnet ID which is added to a specific Amazon VPC.")
    protected String subnetId = null;

    @Configurable(description = "Resource Manager hostname or ip address (must be accessible from nodes)")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = "http://" + generateDefaultRMHostname() + ":8080/connector-iaas";

    @Configurable(description = "URL used to download the node jar on the VM")
    protected String nodeJarURL = linuxInitScriptGenerator.generateDefaultNodeJarURL(rmHostname);

    @Configurable(description = "(optional) Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    protected String additionalProperties = "";

    @Configurable(description = "The timeout for nodes to connect to RM (in ms). After this timeout expired, the node is considered to be lost.")
    protected int nodeTimeout = DEFAULT_NODE_TIMEOUT;// 5 min

    /**
     * Key to retrieve the key pair used to deploy the infrastructure
     */
    private static final String KEY_PAIR_KEY = "keyPair";

    @Override
    public void configure(Object... parameters) {

        logger.info("Validating parameters : " + Arrays.toString(parameters));
        validate(parameters);

        int parameterIndex = 0;
        this.awsKey = parameters[parameterIndex++].toString().trim();
        this.awsSecretKey = parameters[parameterIndex++].toString().trim();
        this.numberOfInstances = parseIntParameter("numberOfInstances", parameters[parameterIndex++]);
        this.numberOfNodesPerInstance = parseIntParameter("numberOfNodesPerInstance", parameters[parameterIndex++]);
        this.image = parameters[parameterIndex++].toString().trim();
        this.vmUsername = parameters[parameterIndex++].toString().trim();
        this.vmKeyPairName = parameters[parameterIndex++].toString().trim();
        this.vmPrivateKey = (byte[]) parameters[parameterIndex++];
        this.ram = parseIntParameter("ram", parameters[parameterIndex++]);
        this.cores = parseIntParameter("cores", parameters[parameterIndex++]);
        //        TODO disable to configure the parameter spotPrice for the moment
        //        this.spotPrice = parameters[parameterIndex++].toString().trim();
        this.securityGroupIds = parameters[parameterIndex++].toString().trim();
        this.subnetId = parameters[parameterIndex++].toString().trim();
        this.rmHostname = parameters[parameterIndex++].toString().trim();
        this.connectorIaasURL = parameters[parameterIndex++].toString().trim();
        this.nodeJarURL = parameters[parameterIndex++].toString().trim();
        this.additionalProperties = parameters[parameterIndex++].toString().trim();
        this.nodeTimeout = parseIntParameter("nodeTimeout", parameters[parameterIndex++]);

        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    private void validate(Object[] parameters) {
        if (parameters == null || parameters.length < NUMBER_OF_PARAMETERS) {
            throw new IllegalArgumentException("Invalid parameters for EC2Infrastructure creation");
        }
        int parameterIndex = 0;
        // awsKey
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("AWS access key ID must be specified");
        }
        parameterIndex++;
        // awsSecretKey
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("AWS secret access key must be specified");
        }
        parameterIndex++;
        // numberOfInstances
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The number of instances to create must be specified");
        }
        parameterIndex++;
        // numberOfNodesPerInstance
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The number of nodes per instance to deploy must be specified");
        }
        parameterIndex++;
        // image
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = DEFAULT_IMAGE;
        }
        if (!parameters[parameterIndex].toString().contains("/")) {
            throw new IllegalArgumentException(String.format("Invalid image %s (image should be in format 'region/ami-id').",
                                                             parameters[parameterIndex]));
        }
        parameterIndex++;
        // vmUsername
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = DEFAULT_VM_USERNAME;
        }
        parameterIndex++;
        // vmKeyPairName
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        parameterIndex++;
        // vmPrivateKey
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        parameterIndex++;
        // ram
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = DEFAULT_RAM;
        }
        parameterIndex++;
        // cores
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = DEFAULT_CORES;
        }
        parameterIndex++;
        //        TODO disable to configure the parameter spotPrice for the moment
        //        // spotPrice
        //        if (parameters[parameterIndex] == null) {
        //            parameters[parameterIndex] = "";
        //        }
        //        parameterIndex++;
        // securityGroupIds
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        parameterIndex++;
        // subnetId
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        parameterIndex++;
        // rmHostname
        checkRMHostname(parameters[parameterIndex].toString());
        parameterIndex++;
        // connectorIaasURL
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The connector-iaas URL must be specified");
        }
        parameterIndex++;
        // nodeJarURL
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The URL for downloading the node jar must be specified");
        }
        parameterIndex++;
        // additionalProperties
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }
        parameterIndex++;
        // nodeTimeout
        if (parameters[parameterIndex] == null) {
            throw new IllegalArgumentException("The node timeout must be specified");
        }
    }

    @Override
    public void acquireAllNodes() {
        deployInstancesWithNodes(numberOfInstances, true);
    }

    @Override
    public void acquireNode() {
        deployInstancesWithNodes(1, true);
    }

    @Override
    public synchronized void acquireNodes(final int numberOfNodes, final Map<String, ?> nodeConfiguration) {
        logger.info(String.format("Acquiring %d nodes with the configuration: %s.", numberOfNodes, nodeConfiguration));

        nodeSource.executeInParallel(() -> {
            if (dynamicAcquireLock.tryLock()) {
                try {
                    int nbInstancesToDeploy = calNumberOfInstancesToDeploy(numberOfNodes,
                                                                           nodeConfiguration,
                                                                           numberOfInstances,
                                                                           numberOfNodesPerInstance);
                    if (nbInstancesToDeploy <= 0) {
                        logger.info("No need to deploy new instances, acquireNodes skipped.");
                        return;
                    }
                    deployInstancesWithNodes(nbInstancesToDeploy, false);
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

    private void deployInstancesWithNodes(int nbInstancesToDeploy, boolean reuseCreatedInstances) {
        connectorIaasController.waitForConnectorIaasToBeUP();

        createAwsInfrastructureIfNeeded();

        String infrastructureId = getInfrastructureId();
        Set<String> instancesIds;
        boolean existPersistedInstanceIds = false;

        // we create new instances in two cases:
        // 1) we don't want to reuse the created instances (e.g., for dynamic policy)
        // 2) we reuse created instances, but there aren't any.
        // For the second case, we check a persisted flag that says whether this infrastructure has already been deployed.
        if (!reuseCreatedInstances || expectInstancesAlreadyCreated(false, true)) {

            // by default, the key pair that is used to deploy the instances has
            // the name of the node source
            String keyPairName = createOrUseKeyPair(infrastructureId, nbInstancesToDeploy);

            instancesIds = createInstances(infrastructureId, keyPairName, nbInstancesToDeploy);

        } else {

            // if the infrastructure was already created, then we need to
            // look at the free instances, if any (the ones on which no node
            // run. In the current implementation, this can only happen when
            // nodes are down. Indeed if they are all removed on purpose, the
            // instance should be shut down). Note that in this case, if the
            // free instances map is empty, no script will be run at all.
            instancesIds = getInstancesWithoutNodesMapCopy().keySet();
            logger.info("Instances ids previously saved which require script re-execution: " + instancesIds);
            existPersistedInstanceIds = true;
        }

        // execute script on instances to deploy or redeploy nodes on them
        for (String currentInstanceId : instancesIds) {
            deployNodesOnInstance(currentInstanceId, existPersistedInstanceIds);

            // in all cases, we must remove the instance from the free
            // instance map as we tried everything to deploy nodes on it
            removeFromInstancesWithoutNodesMap(currentInstanceId);
        }
    }

    private void createAwsInfrastructureIfNeeded() {
        // Create infrastructure if it does not exist
        if (!isCreatedInfrastructure) {
            connectorIaasController.createInfrastructure(getInfrastructureId(),
                                                         awsKey,
                                                         awsSecretKey,
                                                         null,
                                                         DESTROY_INSTANCES_ON_SHUTDOWN);
            isCreatedInfrastructure = true;
        }
    }

    private Set<String> createInstances(String infrastructureId, String keyPairName, int nbInstances) {
        // create instances
        if (spotPrice.isEmpty() && securityGroupIds.isEmpty() && subnetId.isEmpty()) {
            return connectorIaasController.createAwsEc2Instances(infrastructureId,
                                                                 infrastructureId,
                                                                 image,
                                                                 nbInstances,
                                                                 cores,
                                                                 ram,
                                                                 vmUsername,
                                                                 keyPairName);
        } else {
            return connectorIaasController.createAwsEc2InstancesWithOptions(infrastructureId,
                                                                            infrastructureId,
                                                                            image,
                                                                            nbInstances,
                                                                            cores,
                                                                            ram,
                                                                            spotPrice,
                                                                            securityGroupIds,
                                                                            subnetId,
                                                                            null,
                                                                            vmUsername,
                                                                            keyPairName);
        }
    }

    private void deployNodesOnInstance(final String instanceId, final boolean existPersistedInstanceIds) {
        nodeSource.executeInParallel(() -> {
            //change the delimiter between the instanceId and region to make a valid nodeName
            String baseNodeName = getBaseNodeNameFromInstanceId(instanceId);

            List<String> scripts = linuxInitScriptGenerator.buildScript(instanceId,
                                                                        getRmUrl(),
                                                                        rmHostname,
                                                                        nodeJarURL,
                                                                        instanceIdNodeProperty,
                                                                        additionalProperties,
                                                                        nodeSource.getName(),
                                                                        baseNodeName,
                                                                        numberOfNodesPerInstance);

            // declare nodes as "deploying" state to the RM
            List<String> nodeNames = RMNodeStarter.getWorkersNodeNames(baseNodeName, numberOfNodesPerInstance);
            List<String> deployingNodes = addMultipleDeployingNodes(nodeNames,
                                                                    scripts.toString(),
                                                                    "Nodes deployment on AWS EC2",
                                                                    nodeTimeout);
            logger.info("Deploying nodes: " + deployingNodes);
            // run node.jar on the instance with the specified VM credentials
            try {
                connectorIaasController.executeScriptWithKeyAuthentication(getInfrastructureId(),
                                                                           instanceId,
                                                                           scripts,
                                                                           vmUsername,
                                                                           getPersistedKeyPairInfo().getValue());
            } catch (ScriptNotExecutedException e) {
                handleScriptNotExecutedException(existPersistedInstanceIds, instanceId, e);
            }
        });
    }

    private String createOrUseKeyPair(String infrastructureId, int nbInstances) {
        SimpleImmutableEntry<String, String> keyPairInfo;
        if (vmPrivateKey.length == 0 || vmKeyPairName.isEmpty()) {
            // create a key pair in AWS
            try {
                logger.info("Creating an AWS key pair");
                keyPairInfo = connectorIaasController.createAwsEc2KeyPair(infrastructureId,
                                                                          infrastructureId,
                                                                          image,
                                                                          nbInstances,
                                                                          cores,
                                                                          ram);
            } catch (Exception e) {
                logger.warn("Key pair creation in AWS failed. Trying to use persisted key pair.");
                keyPairInfo = handleKeyPairCreationFailure();
            }
        } else {
            // or use the private key provided by the user
            logger.info("Using AWS key pair provided by the user");
            keyPairInfo = new SimpleImmutableEntry<>(vmKeyPairName,
                                                     new String(vmPrivateKey, StandardCharsets.ISO_8859_1));
        }
        persistKeyPairInfo(keyPairInfo);

        // we return the name of the key pair
        return keyPairInfo.getKey();
    }

    private SimpleImmutableEntry<String, String> handleKeyPairCreationFailure() {
        SimpleImmutableEntry<String, String> persistedKeyPair = getPersistedKeyPairInfo();
        if (persistedKeyPair != null && persistedKeyPair.getKey() != null && !persistedKeyPair.getKey().isEmpty()) {
            logger.info("Using key pair '" + persistedKeyPair.getKey() + "' previously persisted");
            return persistedKeyPair;
        } else {
            throw new IllegalStateException("Key pair cannot be created in AWS and there is no persisted private key. Will not deploy infrastructure " +
                                            getInfrastructureId());
        }
    }

    @Override
    public void removeNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        try {
            node.getProActiveRuntime().killNode(node.getNodeInformation().getName());

        } catch (Exception e) {
            logger.warn(e);
        }

        logger.info("Node name :" + node.getNodeInformation().getName() + " InstanceId :" + instanceId);

        unregisterNodeAndRemoveInstanceIfNeeded(instanceId,
                                                node.getNodeInformation().getName(),
                                                getInfrastructureId(),
                                                true);
    }

    @Override
    protected void unregisterNodeAndRemoveInstanceIfNeeded(final String instanceId, final String nodeName,
            final String infrastructureId, final boolean terminateInstanceIfEmpty) {
        setPersistedInfraVariable(() -> {
            // First read from the runtime variables map
            //noinspection unchecked
            nodesPerInstance = (Map<String, Set<String>>) persistedInfraVariables.get(NODES_PER_INSTANCES_KEY);
            // Make modifications to the nodesPerInstance map
            if (nodesPerInstance.get(instanceId) != null) {
                nodesPerInstance.get(instanceId).remove(nodeName);
                logger.info("Removed node: " + nodeName);
                if (nodesPerInstance.get(instanceId).isEmpty()) {
                    logger.info("Instance :" + instanceId + " is empty ");
                    if (terminateInstanceIfEmpty) {
                        logger.info("Call terminate instance for: " + instanceId);
                        connectorIaasController.terminateInstance(infrastructureId, instanceId);
                        logger.info("Instance terminated: " + instanceId);
                    }
                    nodesPerInstance.remove(instanceId);
                    logger.info("Removed instance: " + instanceId);
                }
                // Finally write to the runtime variable map
                decrementNumberOfAcquiredNodesWithLockAndPersist();
                persistedInfraVariables.put(NODES_PER_INSTANCES_KEY, Maps.newHashMap(nodesPerInstance));
            } else {
                logger.error("Cannot remove node " + nodeName + " because instance " + instanceId +
                             " is not registered");
            }
            return null;
        });
    }

    @Override
    public void notifyAcquiredNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        addNewNodeForInstance(instanceId, node.getNodeInformation().getName());
    }

    @Override
    protected void notifyDeployingNodeLost(String pnURL) {
        super.notifyDeployingNodeLost(pnURL);
        logger.info("Unregistering the lost node " + pnURL);
        RMDeployingNode currentNode = getDeployingOrLostNode(pnURL);
        String instanceId = parseInstanceIdFromNodeName(currentNode.getNodeName());

        // Delete the instance when instance doesn't contain any other deploying nodes or persisted nodes
        if (!existOtherDeployingNodesOnInstance(currentNode, instanceId) &&
            !existRegisteredNodesOnInstance(instanceId)) {
            connectorIaasController.terminateInstance(getInfrastructureId(), instanceId);
        }
    }

    private boolean existOtherDeployingNodesOnInstance(RMDeployingNode currentNode, String instanceTag) {
        for (RMDeployingNode node : getDeployingAndLostNodes()) {
            if (!node.equals(currentNode) && !node.isLost() &&
                parseInstanceIdFromNodeName(node.getNodeName()).equals(instanceTag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse the instanceId (i.e., baseNodeName) from the complete nodeName.
     * The nodeName may contain an index (e.g., _0, _1) as suffix or not.
     * @param nodeName (e.g., region__instance-id_0, region__instance-id_1, or region__instance-id)
     * @return instanceId with AWS format (e.g., region/instance-id)
     */
    private static String parseInstanceIdFromNodeName(final String nodeName) {
        int indexNodeSeparator = nodeName.lastIndexOf(NODE_INDEX_DELIMITER);
        // when nodeName contains no NODE_INDEX_DELIMITER, baseNodeName is same as nodeName, otherwise it's the part before NODE_INDEX_DELIMITER
        String baseNodeName = (indexNodeSeparator == -1) ? nodeName : nodeName.substring(0, indexNodeSeparator);
        return getInstanceIdFromBaseNodeName(baseNodeName);
    }

    private static String getInstanceIdFromBaseNodeName(final String baseNodeName) {
        return baseNodeName.replaceFirst(INSTANCE_ID_REGION_DELIMITER_IN_NODENAME, INSTANCE_ID_REGION_DELIMITER);
    }

    private static String getBaseNodeNameFromInstanceId(final String instanceId) {
        return instanceId.replaceFirst(INSTANCE_ID_REGION_DELIMITER, INSTANCE_ID_REGION_DELIMITER_IN_NODENAME);
    }

    @Override
    public String getDescription() {
        return "Handles nodes from the Amazon Elastic Compute Cloud Service.";
    }

    @Override
    public void shutDown() {
        super.shutDown();
        String infrastructureId = getInfrastructureId();
        logger.info(String.format("Deleting infrastructure (%s) and its instances", infrastructureId));
        connectorIaasController.terminateInfrastructure(infrastructureId, true);
        logger.info(String.format("Successfully deleted infrastructure (%s) and its instances.", infrastructureId));
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
            logger.warn(e);
            return "localhost";
        }
    }

    private void persistKeyPairInfo(final SimpleImmutableEntry<String, String> keyPair) {
        setPersistedInfraVariable(() -> persistedInfraVariables.put(KEY_PAIR_KEY, keyPair));
    }

    @SuppressWarnings("unchecked")
    private SimpleImmutableEntry<String, String> getPersistedKeyPairInfo() {
        return getPersistedInfraVariable(() -> (SimpleImmutableEntry<String, String>) persistedInfraVariables.get(KEY_PAIR_KEY));
    }

}
