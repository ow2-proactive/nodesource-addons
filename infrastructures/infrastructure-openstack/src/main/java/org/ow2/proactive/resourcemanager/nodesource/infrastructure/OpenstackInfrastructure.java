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
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.util.ProActiveCounter;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.LinuxInitScriptGenerator;

import com.google.common.collect.Maps;


/**
 * @author ActiveEon Team
 * @since 2019-04-01
 */
public class OpenstackInfrastructure extends AbstractAddonInfrastructure {

    /**
     *  Openstack infrastructure parameters
     **/
    private static final Logger logger = Logger.getLogger(OpenstackInfrastructure.class);

    private static final int NUMBER_OF_PARAMETERS = 17;

    public static final String INSTANCE_TAG_NODE_PROPERTY = "instanceTag";

    public static final String INFRASTRUCTURE_TYPE = "openstack-nova";

    private final transient Lock acquireLock = new ReentrantLock();

    private boolean isInitializedAndCreated = false;

    private final transient LinuxInitScriptGenerator linuxInitScriptGenerator = new LinuxInitScriptGenerator();

    /**
     *  Fields of the Openstack infrastructure form (in the RM portal)
     */
    @Configurable(description = "Openstack username")
    protected String username = null;

    @Configurable(description = "Openstack password")
    protected String password = null;

    @Configurable(description = "Openstack user domain")
    protected String domain = null;

    @Configurable(description = "Openstack identity endPoint")
    protected String endpoint = null;

    @Configurable(description = "Openstack scope prefix")
    protected String scopePrefix = null;

    @Configurable(description = "Openstack scope value")
    protected String scopeValue = null;

    @Configurable(description = "Openstack region")
    protected String region = null;

    @Configurable(description = "Openstack identity version")
    protected String identityVersion = null;

    @Configurable(description = "Openstack image")
    protected String image = null;

    @Configurable(description = "Flavor type of OpenStack")
    protected String flavor = null;

    @Configurable(description = "Public key name for Openstack instance")
    protected String publicKeyName = null;

    @Configurable(description = "Total (max) number of instances to create")
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = linuxInitScriptGenerator.generateDefaultIaasConnectorURL(generateDefaultRMHostname());

    @Configurable(description = "Resource Manager hostname or ip address")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Command used to download the node jar")
    protected String downloadCommand = linuxInitScriptGenerator.generateDefaultDownloadCommand(rmHostname);

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    @Configurable(description = "Estimated startup time of the nodes (including the startup time of VMs)")
    protected long nodesInitDelay = 240000;

    /**
     * Dynamic policy parameters
     **/
    protected static final String TOTAL_NUMBER_OF_NODES_KEY = "TOTAL_NUMBER_OF_NODES";

    private static final String INIT_DELAY_KEY = "INIT_DELAY";

    private static final String REFRESH_TIME_KEY = "REFRESH_TIME";

    private static final String MAX_NODES_KEY = "MAX_NODES";

    int nbOfNodes;

    int totalNodes;

    int existingNodes;

    int maxNodes;

    long refreshTime;

    int instancesToDeploy;

    @Override
    public void configure(Object... parameters) {

        logger.info("Validating parameters : " + java.util.Arrays.toString(parameters));
        validate(parameters);

        this.username = parameters[0].toString().trim();
        this.password = parameters[1].toString().trim();
        this.domain = parameters[2].toString().trim();
        this.endpoint = parameters[3].toString().trim();
        this.scopePrefix = parameters[4].toString().trim();
        this.scopeValue = parameters[5].toString().trim();
        this.region = parameters[6].toString().trim();
        this.identityVersion = parameters[7].toString().trim();
        this.image = parameters[8].toString().trim();
        this.flavor = parameters[9].toString().trim();
        this.publicKeyName = parameters[10].toString().trim();
        this.numberOfInstances = Integer.parseInt(parameters[11].toString().trim());
        this.numberOfNodesPerInstance = Integer.parseInt(parameters[12].toString().trim());
        this.connectorIaasURL = parameters[13].toString().trim();
        this.rmHostname = parameters[14].toString().trim();
        this.downloadCommand = parameters[15].toString().trim();
        this.additionalProperties = parameters[16].toString().trim();
        this.nodesInitDelay = Long.parseLong(parameters[17].toString().trim());

        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    private void validate(Object[] parameters) {

        if (parameters == null || parameters.length < NUMBER_OF_PARAMETERS) {
            throw new IllegalArgumentException("Invalid parameters for Openstack Infrastructure creation");
        }

        if (parameters[0] == null) {
            throw new IllegalArgumentException("Openstack username must be specified");
        }

        if (parameters[1] == null) {
            throw new IllegalArgumentException("Openstack password must be specified");
        }

        if (parameters[2] == null) {
            throw new IllegalArgumentException("Openstack user domain must be specified");
        }

        if (parameters[3] == null) {
            throw new IllegalArgumentException("Openstack endpoint must be specified");
        }

        if (parameters[6] == null) {
            throw new IllegalArgumentException("Openstack region must be specified");
        }

        if (parameters[8] == null) {
            throw new IllegalArgumentException("Openstack image id must be specified");
        }

        if (parameters[9] == null) {
            throw new IllegalArgumentException("Openstack flavor must be specified");
        }

        if (parameters[11] == null) {
            throw new IllegalArgumentException("The number of instances to create must be specified");
        }

        if (parameters[12] == null) {
            throw new IllegalArgumentException("The number of nodes per instance to deploy must be specified");
        }

        if (parameters[13] == null) {
            throw new IllegalArgumentException("The connector-iaas URL must be specified");
        }

        if (parameters[14] == null) {
            throw new IllegalArgumentException("RM host (hostname or IP address) must be specified");
        }

        if (parameters[15] == null) {
            throw new IllegalArgumentException("The command to download 'node.jar' must be specified");
        }

        if (parameters[16] == null) {
            throw new IllegalArgumentException("Additional properties to run ProActive node must be specified");
        }
    }

    @Override
    public void acquireNode() {

        connectorIaasController.waitForConnectorIaasToBeUP();

        createOpenstackInfrastructure();

        for (int i = 1; i <= numberOfInstances; i++) {
            String instanceTag = getInfrastructureId() + "_" + ProActiveCounter.getUniqID();
            List<String> scripts = createScripts(instanceTag, instanceTag, numberOfNodesPerInstance);
            createOpenstackInstance(instanceTag, scripts);

            // Declare nodes are deploying
            Executors.newCachedThreadPool().submit(() -> {
                declareNodesAsDeploying(numberOfNodesPerInstance, instanceTag);
            });
        }
    }

    @Override
    public void acquireAllNodes() {
        acquireNode();
    }

    @Override
    public synchronized void acquireNodes(final int numberOfNodes, final Map<String, ?> nodeConfiguration) {
        this.nodeSource.executeInParallel(() -> {
            if (acquireLock.tryLock()) {
                try {
                    internalAcquireNodes(numberOfNodes, nodeConfiguration);
                } catch (Exception e) {
                    logger.error("Error during node acquisition", e);
                } finally {
                    acquireLock.unlock();
                }
            } else {
                logger.info("acquireNodes skipped because infrastructure is busy.");
            }
        });
    }

    @Override
    public void removeNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        try {
            node.getProActiveRuntime().killNode(node.getNodeInformation().getName());

        } catch (Exception e) {
            logger.warn(e);
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
        return "Handles ProActive nodes using Nova compute service of Openstack Cloud.";
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

    @Override
    protected String getInstanceIdProperty(Node node) throws RMException {
        try {
            return node.getProperty(INSTANCE_TAG_NODE_PROPERTY);
        } catch (ProActiveException e) {
            throw new RMException(e);
        }
    }

    @Override
    public void shutDown() {
        String infrastructureId = getInfrastructureId();
        logger.info("Deleting infrastructure : " + infrastructureId + " and its underlying instances");
        connectorIaasController.terminateInfrastructure(infrastructureId, true);
    }

    @Override
    protected void unregisterNodeAndRemoveInstanceIfNeeded(final String instanceTag, final String nodeName,
            final String infrastructureId, final boolean terminateInstanceIfEmpty) {
        setPersistedInfraVariable(() -> {
            // First read from the runtime variables map
            //noinspection unchecked
            nodesPerInstance = (Map<String, Set<String>>) persistedInfraVariables.get(NODES_PER_INSTANCES_KEY);
            // Make modifications to the nodesPerInstance map
            if (nodesPerInstance.get(instanceTag) != null) {
                nodesPerInstance.get(instanceTag).remove(nodeName);
                logger.info("Removed node: " + nodeName);
                if (nodesPerInstance.get(instanceTag).isEmpty()) {
                    if (terminateInstanceIfEmpty) {
                        connectorIaasController.terminateInstanceByTag(infrastructureId, instanceTag);
                        logger.info("Instance terminated: " + instanceTag);
                    }
                    nodesPerInstance.remove(instanceTag);
                    logger.info("Removed instance: " + instanceTag);
                }
                // Finally write to the runtime variable map
                decrementNumberOfAcquiredNodesWithLockAndPersist();
                persistedInfraVariables.put(NODES_PER_INSTANCES_KEY, Maps.newHashMap(nodesPerInstance));
            } else {
                logger.error("Cannot remove node " + nodeName + " because instance " + instanceTag +
                             " is not registered");
            }
            return null;
        });
    }

    private void internalAcquireNodes(int numberOfNodes, Map<String, ?> dynamicPolicyParameters) {

        nbOfNodes = numberOfNodes;

        if (nbOfNodes > 0 && checkDynamicPolicyParameters(nbOfNodes, dynamicPolicyParameters)) {

            // Create Openstack infrastructure (if it does not exist) and initialize its persistent variables
            initializeOpenstackInfrastructure();

            // Determine the number of instances to deploy and check it
            if (!checkOpenstackInstancesToDeploy()) {
                return;
            }

            int nbOfDeployedNodes = 0;

            Map<String, Set<String>> instancesAndNodesToDeploy = Maps.newHashMap();

            for (int i = 0; i < instancesToDeploy; i++) {

                // Determine the numbe rof nodes to start in the current instance
                int nodesInCurrentInstance = (existingNodes + nbOfDeployedNodes +
                                              numberOfNodesPerInstance <= maxNodes) ? numberOfNodesPerInstance
                                                                                    : nbOfNodes - nbOfDeployedNodes;
                // Determine the instance tag
                String instanceTag = getInfrastructureId() + "_" + ProActiveCounter.getUniqID();
                logger.info("Deploying Openstack instance with tag " + instanceTag + " and the number of nodes " +
                            nodesInCurrentInstance);

                // Build nodes'start scripts and deploy instance
                List<String> scripts = createScripts(instanceTag, instanceTag, nodesInCurrentInstance);
                createOpenstackInstance(instanceTag, scripts);

                // Declare deploying nodes
                Set<String> deployedNodes = declareNodesAsDeploying(nodesInCurrentInstance, instanceTag);

                // Update the number of deployed instances and nodes
                nbOfDeployedNodes += nodesInCurrentInstance;
                instancesAndNodesToDeploy.put(instanceTag, deployedNodes);
            }

            if (!waitForNodesToBeUp(existingNodes + nbOfDeployedNodes, refreshTime)) {
                logger.info("Deployed Openstack instances and nodes will be removed");
                removeDeployedInstancesAndNodes(instancesAndNodesToDeploy);
            }

            logger.info("Persistence information of the infrastructure '" + getInfrastructureId() + "' updated: " +
                        persistedInfraVariables.toString());
        }
    }

    private void initializeOpenstackInfrastructure() {
        if (!isInitializedAndCreated) {
            initializePersistedInfraVariables();
            logger.info("Dynamic policy variables initialized");

            connectorIaasController.waitForConnectorIaasToBeUP();
            createOpenstackInfrastructure();
            logger.info("Openstack infrastructure created");

            isInitializedAndCreated = true;
        }
    }

    // check that all the dynamic policy parameters exist and are correct
    private boolean checkDynamicPolicyParameters(int numberOfNodes, Map<String, ?> dynamicPolicyParameters) {

        if (!dynamicPolicyParameters.containsKey(TOTAL_NUMBER_OF_NODES_KEY)) {
            logger.info("The dynamic policy parameters must include the total number of nodes");
            return false;
        } else if (!dynamicPolicyParameters.containsKey(MAX_NODES_KEY)) {
            logger.info("The dynamic policy parameters must include the maximal number of nodes");
            return false;
        } else if (!dynamicPolicyParameters.containsKey(INIT_DELAY_KEY)) {
            logger.info("The dynamic policy parameters must include the initialization delay of nodes");
            return false;
        } else if (!dynamicPolicyParameters.containsKey(REFRESH_TIME_KEY)) {
            logger.info("The dynamic policy parameters must include the refresh time of the policy");
            return false;
        }

        existingNodes = getNumberOfAcquiredNodesWithLock();

        totalNodes = (Integer) dynamicPolicyParameters.get(TOTAL_NUMBER_OF_NODES_KEY);

        maxNodes = (Integer) dynamicPolicyParameters.get(MAX_NODES_KEY);

        refreshTime = (Long) dynamicPolicyParameters.get(REFRESH_TIME_KEY);

        if (existingNodes == totalNodes) {
            logger.info("The number of existing nodes (" + existingNodes +
                        ") is equal to total number of nodes required by the dynamic policy (" + totalNodes +
                        "), so node acquisition is skipped!");
            return false;
        }

        if (existingNodes + numberOfNodes != totalNodes) {
            logger.warn("Dynamic policy (of Openstack infrastructure) is misconfigured. The total number of nodes (" +
                        totalNodes + ") does not correspond to the sum of existing nodes (" + existingNodes +
                        ") and the new nodes to add (" + numberOfNodes + ").");
            return false;
        }

        // check that the maximal number of nodes is not reached
        if (maxNodes - (existingNodes + numberOfNodes) < 0) {

            logger.warn("The sum of existing nodes (" + existingNodes + ") and the required nodes (" + numberOfNodes +
                        ") is greater than the maximal number of nodes (" + maxNodes +
                        ") allowed by the dynamic policy, so deploying only " + (maxNodes - existingNodes) + " nodes.");

            nbOfNodes = maxNodes - existingNodes;
        }

        return true;
    }

    private boolean checkOpenstackInstancesToDeploy() {

        int existingInstances = getNumberOfInstancesWithLock();
        logger.info("number of existing instances: " + existingInstances);

        int preliminarynNumberOfInstances = (int) Math.ceil((double) nbOfNodes / (double) numberOfNodesPerInstance);
        instancesToDeploy = Math.min(preliminarynNumberOfInstances, numberOfInstances);

        if (instancesToDeploy == 0) {
            logger.info("No need to add new instances to the infrastructure: " + this.getInfrastructureId());
            return false;
        } else if (existingInstances + instancesToDeploy > numberOfInstances) {
            logger.info("The sum of existing instances (" + existingInstances + ") and the instances to deploy (" +
                        instancesToDeploy +
                        ") exceeds to total number of instances allowed by Openstack infrastructure (" +
                        numberOfInstances + "), so node acquisition is skipped!");
            return false;
        }

        logger.info("Openstack instances to deploy: " + instancesToDeploy);
        logger.info("Total nodes to start in Openstack instances: " + nbOfNodes);

        return true;
    }

    protected Set<String> declareNodesAsDeploying(int nodesInCurrentInstance, String nodeBaseName) {

        Set<String> nodes = new HashSet<>();

        for (int j = 0; j < nodesInCurrentInstance; j++) {
            String nodeName = (nodesInCurrentInstance == 1) ? nodeBaseName : nodeBaseName + "_" + j;
            String nodeUrl = addDeployingNode(nodeName,
                                              "Initiated by Openstack infrastructure",
                                              "Nodes running in Openstack compute instances",
                                              nodesInitDelay);
            logger.info("Deploying node " + nodeName + " [" + nodeUrl + "]");
            nodes.add(nodeName);
        }

        return nodes;
    }

    private void createOpenstackInfrastructure() {
        connectorIaasController.createOpenstackInfrastructure(getInfrastructureId(),
                                                              username,
                                                              password,
                                                              domain,
                                                              scopePrefix,
                                                              scopeValue,
                                                              region,
                                                              identityVersion,
                                                              endpoint,
                                                              true);
    }

    private List<String> createScripts(String instanceTag, String nodeName, int nbNodes) {

        return linuxInitScriptGenerator.buildScript(instanceTag,
                                                    getRmUrl(),
                                                    rmHostname,
                                                    INSTANCE_TAG_NODE_PROPERTY,
                                                    additionalProperties,
                                                    nodeSource.getName(),
                                                    nodeName,
                                                    nbNodes);
    }

    private void createOpenstackInstance(String instanceTag, List<String> scripts) {
        connectorIaasController.createOpenstackInstance(getInfrastructureId(),
                                                        instanceTag,
                                                        image,
                                                        1,
                                                        flavor,
                                                        publicKeyName,
                                                        scripts);
    }

    private boolean waitForNodesToBeUp(int totalNodes, long refreshTime) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<Object> task = () -> {

            while (getNumberOfAcquiredNodesWithLock() < totalNodes) {
                logger.info("Waiting for " + (totalNodes - getNumberOfAcquiredNodesWithLock()) + " nodes to be up");
                Thread.sleep(refreshTime);
            }

            return nodeSource.getNodesCount();
        };
        Future<Object> future = executor.submit(task);
        try {
            future.get(nodesInitDelay, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | InterruptedException | ExecutionException ex) {
            logger.error("A problem occurred while acquiring nodes.", ex);
            return false;
        } finally {
            future.cancel(true);
        }
    }

    private void removeDeployedInstancesAndNodes(Map<String, Set<String>> deployedInstancesAndNodes) {
        for (Map.Entry<String, Set<String>> instanceWithNodes : deployedInstancesAndNodes.entrySet()) {
            String instanceTag = instanceWithNodes.getKey();
            for (String nodeName : instanceWithNodes.getValue()) {
                unregisterNodeAndRemoveInstanceIfNeeded(instanceTag, nodeName, getInfrastructureId(), true);
            }
            connectorIaasController.terminateInstanceByTag(getInfrastructureId(), instanceTag);
        }
    }

    private int getNumberOfInstancesWithLock() {
        if (this.persistedInfraVariables.containsKey(NODES_PER_INSTANCES_KEY)) {
            //noinspection unchecked
            return ((Map<String, Set<String>>) persistedInfraVariables.get(NODES_PER_INSTANCES_KEY)).size();
        } else {
            return 0;
        }
    }
}
