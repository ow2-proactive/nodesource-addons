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

import java.security.KeyException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.util.ProActiveCounter;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.InitScriptGenerator;

import com.google.common.collect.Maps;

import lombok.Getter;


/**
 * @author ActiveEon Team
 * @since 2019-04-01
 */
public class OpenstackInfrastructure extends AbstractAddonInfrastructure {

    /**
     *  Openstack infrastructure parameters
     **/
    private static final Logger logger = Logger.getLogger(OpenstackInfrastructure.class);

    @Getter
    private final String instanceIdNodeProperty = "instanceTag";

    public static final String INFRASTRUCTURE_TYPE = "openstack-nova";

    private static final long DEFAULT_NODES_INIT_DELAY = 4 * 60 * 1000;// 4 min

    private final transient Lock acquireLock = new ReentrantLock();

    private boolean isInitializedAndCreated = false;

    private final transient InitScriptGenerator initScriptGenerator = new InitScriptGenerator();

    /**
     *  Fields of the Openstack infrastructure form (in the RM portal)
     */
    @Configurable(description = "Openstack username", sectionSelector = 1, important = true)
    protected String username = null;

    @Configurable(description = "Openstack password", password = true, sectionSelector = 1, important = true)
    protected String password = null;

    @Configurable(description = "Openstack user domain", sectionSelector = 1, important = true)
    protected String domain = null;

    @Configurable(description = "Openstack identity endPoint", sectionSelector = 1, important = true)
    protected String endpoint = null;

    @Configurable(description = "Openstack scope prefix", sectionSelector = 1, important = true)
    protected String scopePrefix = null;

    @Configurable(description = "Openstack scope value", sectionSelector = 1, important = true)
    protected String scopeValue = null;

    @Configurable(description = "Openstack region", sectionSelector = 1, important = true)
    protected String region = null;

    @Configurable(description = "Openstack identity version", sectionSelector = 1, important = true)
    protected String identityVersion = null;

    @Configurable(description = "Openstack image", sectionSelector = 3, important = true)
    protected String image = null;

    @Configurable(description = "Flavor type of OpenStack", sectionSelector = 3, important = true)
    protected String flavor = null;

    @Configurable(description = "(optional) Network id for the openstack instance", sectionSelector = 3)
    protected String networkId = null;

    @Configurable(description = "(optional) Public key name for Openstack instance", sectionSelector = 3)
    protected String publicKeyName = null;

    @Configurable(description = "Total (max) number of instances to create", sectionSelector = 2, important = true)
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance", sectionSelector = 2, important = true)
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Connector-iaas URL", sectionSelector = 4)
    protected String connectorIaasURL = InitScriptGenerator.generateDefaultIaasConnectorURL(generateDefaultRMHostname());

    @Configurable(description = "Resource Manager hostname or ip address", sectionSelector = 4)
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "URL used to download the node jar on the instance", sectionSelector = 5)
    protected String nodeJarURL = InitScriptGenerator.generateDefaultNodeJarURL(generateDefaultRMHostname());

    @Configurable(description = "(optional) Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")", sectionSelector = 5)
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    @Configurable(description = "(optional, default value: " + DEFAULT_NODES_INIT_DELAY +
                                ") Estimated startup time of the nodes (including the startup time of VMs)", sectionSelector = 5)
    protected long nodesInitDelay = DEFAULT_NODES_INIT_DELAY;

    @Configurable(textArea = true, description = "VM startup script to launch the ProActive nodes (optional). Please refer to the documentation for full description.", sectionSelector = 5)
    protected String startupScript = initScriptGenerator.getDefaultLinuxStartupScript();

    // The index of the infrastructure configurable parameters.
    protected enum Indexes {
        USERNAME(0),
        PASSWORD(1),
        DOMAIN(2),
        ENDPOINT(3),
        SCOPE_PREFIX(4),
        SCOPE_VALUE(5),
        REGION(6),
        IDENTITY_VERSION(7),
        IMAGE(8),
        FLAVOR(9),
        NETWORK(10),
        PUBLIC_KEY_NAME(11),
        NUMBER_OF_INSTANCES(12),
        NUMBER_OF_NODES_PER_INSTANCE(13),
        CONNECTOR_IAAS_URL(14),
        RM_HOSTNAME(15),
        NODE_JAR_URL(16),
        ADDITIONAL_PROPERTIES(17),
        NODES_INIT_DELAY(18),
        STARTUP_SCRIPT(19);

        protected int index;

        Indexes(int index) {
            this.index = index;
        }
    }

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

    private Map<String, String> meta = new HashMap<>();

    {
        meta.putAll(super.getMeta());
        meta.put(InfrastructureManager.ELASTIC, "true");
    }

    @Override
    public void configure(Object... parameters) {

        logger.info("Validating parameters");
        if (parameters == null || parameters.length < Indexes.values().length) {
            throw new IllegalArgumentException("Invalid parameters for Openstack Infrastructure creation");
        }

        this.username = parseMandatoryParameter("username", parameters[Indexes.USERNAME.index]);
        this.password = parseMandatoryParameter("password", parameters[Indexes.PASSWORD.index]);
        this.domain = parseMandatoryParameter("domain", parameters[Indexes.DOMAIN.index]);
        this.endpoint = parseMandatoryParameter("endpoint", parameters[Indexes.ENDPOINT.index]);
        this.scopePrefix = parseMandatoryParameter("scopePrefix", parameters[Indexes.SCOPE_PREFIX.index]);
        this.scopeValue = parseMandatoryParameter("scopeValue", parameters[Indexes.SCOPE_VALUE.index]);
        this.region = parseMandatoryParameter("region", parameters[Indexes.REGION.index]);
        this.identityVersion = parseMandatoryParameter("identityVersion", parameters[Indexes.IDENTITY_VERSION.index]);
        this.image = parseMandatoryParameter("image", parameters[Indexes.IMAGE.index]);
        this.flavor = parseMandatoryParameter("flavor", parameters[Indexes.FLAVOR.index]);
        this.networkId = parseOptionalParameter(parameters[Indexes.NETWORK.index]);
        this.publicKeyName = parseOptionalParameter(parameters[Indexes.PUBLIC_KEY_NAME.index]);
        this.numberOfInstances = parseIntParameter("numberOfInstances", parameters[Indexes.NUMBER_OF_INSTANCES.index]);
        this.numberOfNodesPerInstance = parseIntParameter("numberOfNodesPerInstance",
                                                          parameters[Indexes.NUMBER_OF_NODES_PER_INSTANCE.index]);
        this.connectorIaasURL = parseMandatoryParameter("connectorIaasURL",
                                                        parameters[Indexes.CONNECTOR_IAAS_URL.index]);
        this.rmHostname = parseMandatoryParameter("rmHostname", parameters[Indexes.RM_HOSTNAME.index]);
        this.nodeJarURL = parseMandatoryParameter("nodeJarURL", parameters[Indexes.NODE_JAR_URL.index]);
        this.additionalProperties = parseOptionalParameter(parameters[Indexes.ADDITIONAL_PROPERTIES.index]);
        this.nodesInitDelay = parseLongParameter("nodesInitDelay",
                                                 parameters[Indexes.NODES_INIT_DELAY.index],
                                                 DEFAULT_NODES_INIT_DELAY);
        this.startupScript = parseOptionalParameter(parameters[Indexes.STARTUP_SCRIPT.index],
                                                    initScriptGenerator.getDefaultLinuxStartupScript());
        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    @Override
    public void acquireNode() {

        connectorIaasController.waitForConnectorIaasToBeUP();

        createOpenstackInfrastructure();

        for (int i = 1; i <= numberOfInstances; i++) {
            String instanceTag = getInfrastructureId() + "_" + ProActiveCounter.getUniqID();
            List<String> scripts = createScripts(instanceTag, instanceTag, numberOfNodesPerInstance);
            logger.info("start up script: " + scripts);
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
                logger.info("start up script: " + scripts);
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

        try {

            return initScriptGenerator.buildLinuxScript(startupScript,
                                                        instanceTag,
                                                        getRmUrl(),
                                                        rmHostname,
                                                        nodeJarURL,
                                                        instanceIdNodeProperty,
                                                        additionalProperties,
                                                        nodeSource.getName(),
                                                        nodeName,
                                                        nbNodes,
                                                        getCredentials());
        } catch (KeyException a) {
            logger.error("A problem occurred while acquiring user credentials path. The node startup script will be empty.");
            return new ArrayList<>();
        }
    }

    private void createOpenstackInstance(String instanceTag, List<String> scripts) {
        connectorIaasController.createOpenstackInstance(getInfrastructureId(),
                                                        instanceTag,
                                                        image,
                                                        1,
                                                        flavor,
                                                        publicKeyName,
                                                        networkId,
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

    @Override
    public Map<Integer, String> getSectionDescriptions() {
        Map<Integer, String> sectionDescriptions = super.getSectionDescriptions();
        sectionDescriptions.put(1, "Openstack Configuration");
        sectionDescriptions.put(2, "Deployment Configuration");
        sectionDescriptions.put(3, "VM Configuration");
        sectionDescriptions.put(4, "PA Server Configuration");
        sectionDescriptions.put(5, "Node Configuration");
        return sectionDescriptions;
    }

    @Override
    public Map<String, String> getMeta() {
        return meta;
    }
}
