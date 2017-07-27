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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;


/**
 * This class factorizes some common parts of the different node source addons
 * and provides methods to wrap the accesses to the variables of the
 * infrastructure saved in database in case the infrastructure state needs to
 * be recovered.
 *
 * @author ActiveEon Team
 * @since 21/07/17
 */
public abstract class AbstractAddonInfrastructure extends InfrastructureManager {

    private static final Logger logger = Logger.getLogger(AbstractAddonInfrastructure.class);

    /**
     * Key to retrieve the nodesPerInstance map within the
     * {@link InfrastructureManager#runtimeVariables} map of the
     * infrastructure, which holds the variables that are saved in database.
     */
    private static final String NODES_PER_INSTANCES_KEY = "nodesPerInstance";

    /**
     * Key to retrieve in the {@link InfrastructureManager#runtimeVariables}
     * map a flag that says whether the infrastructure has already been created.
     */
    private static final String INFRASTRUCTURE_CREATED_FLAG_KEY = "infrastructureCreatedFlag";

    private static final String NB_REMOVED_NODES_PER_INSTANCE_KEY = "nbRemovedNodesPerInstance";

    private static final String FREE_INSTANCES_MAP_KEY = "freeInstancesMap";

    /**
     * The controller is transient as it is not supposed to be serialized or
     * saved in database. It should be recreated at start up.
     */
    protected transient ConnectorIaasController connectorIaasController = null;

    /**
     * Information about instances and their nodes. Maps the instance
     * identifier to the name of the nodes that belong to it.
     */
    private Map<String, Set<String>> nodesPerInstance;

    /**
     * Used to track the nodes that are not in the
     * {@link AbstractAddonInfrastructure#nodesPerInstance} map, because
     * either they have been removed or because they are down. This is used in
     * the redeployment on down nodes logic and the nodes recovery logic.
     */
    private Map<String, Integer> nbRemovedNodesPerInstance;

    /**
     * Typically, once all nodes of an instance are counted in the
     * {@link AbstractAddonInfrastructure#nbRemovedNodesPerInstance} map, the
     * instance entry is removed in this map and the same entry is created in
     * the {@link AbstractAddonInfrastructure#freeInstancesMap} map.
     * Infrastructure implementations then should check this map when they
     * want to acquire a node.
     */
    private Map<String, Integer> freeInstancesMap;

    /**
     * Default constructor
     */
    protected AbstractAddonInfrastructure() {
        nodesPerInstance = new HashMap<>();
        nbRemovedNodesPerInstance = new HashMap<>();
        freeInstancesMap = new HashMap<>();
    }

    @Override
    public void notifyDownNode(String nodeName, String nodeUrl, Node node) throws RMException {
        // if the node object is null, it means that we are in a recovery of
        // the resource manager, where the node cannot be found anymore, there
        // is hardly something that we can do, apart from making sure that
        // this node url is taken into account in the removed nodes.
        if (node != null) {
            String instanceId = getInstanceIdProperty(node);
            removeNode(node);
            incrementRemovedNodes(node.getNodeInformation().getName(), instanceId);
        } else {
            logger.warn("The information of down node " + nodeName + " cannot be retrieved. Not handling down node");
        }
    }

    @Override
    public void onDownNodeReconnection(Node node) {
        String nodeName = node.getNodeInformation().getName();
        try {
            String instanceId = getInstanceIdProperty(node);
            decrementRemovedNodes(nodeName, instanceId);
        } catch (RMException e) {
            logger.warn("An exception occurred during the reconnection of down node " + nodeName, e);
        }
    }

    @Override
    public void shutDown() {
        compareAndSetInfrastructureCreatedFlag(true, false);
    }

    @Override
    protected void initializeRuntimeVariables() {
        runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstance);
        runtimeVariables.put(NB_REMOVED_NODES_PER_INSTANCE_KEY, nbRemovedNodesPerInstance);
        runtimeVariables.put(FREE_INSTANCES_MAP_KEY, freeInstancesMap);
        runtimeVariables.put(INFRASTRUCTURE_CREATED_FLAG_KEY, false);
    }

    /**
     * Implementations of this method should return the identifier of the
     * instance where this node is hosted. This is specific to the
     * infrastructure implementation.
     * @param node the node for which the instance is looked for
     * @return the identifier of the instance
     */
    protected abstract String getInstanceIdProperty(Node node) throws RMException;

    protected String getInfrastructureId() {
        return nodeSource.getName().trim().replace(" ", "_").toLowerCase();
    }

    /**
     * @return a shallow copy of the nodes per instance map that is read from
     * the runtime variables. Since all elements in this copy are of
     * {@link java.lang.String} type, we are sure that the elements of the
     * original list cannot be affected.
     */
    protected Map<String, Set<String>> getNodesPerInstancesMapCopy() {
        return getRuntimeVariable(new RuntimeVariablesHandler<Map<String, Set<String>>>() {
            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Set<String>> handle() {
                nodesPerInstance = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                return new HashMap<>(nodesPerInstance);
            }
        });
    }

    /**
     * This method puts a new node name entry for the given instance. It does
     * that within a write lock acquired, that is exposed by the super class.
     * At the end of this method ensures, the nodesPerInstance map is saved in
     * database.
     * @param instanceId the identifier of the instance
     * @param nodeName the name of the new node that belongs to this instance
     */
    protected void addNewNodeForInstance(final String instanceId, final String nodeName) {
        setRuntimeVariable(new RuntimeVariablesHandler<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void handle() {
                // first read from the runtime variables map
                nodesPerInstance = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                // make modifications to the nodesPerInstance map
                if (!nodesPerInstance.containsKey(instanceId)) {
                    nodesPerInstance.put(instanceId, new HashSet<String>());
                }
                nodesPerInstance.get(instanceId).add(nodeName);
                // finally write to the runtime variable map
                runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstance);
                return null;
            }
        });
    }

    /**
     * This method removes a node name entry for the given instance, and call
     * the instance termination mechanism if there no more nodes attached to
     * this instance. It does all that within a write lock acquired, that is
     * exposed by the super class. At the end of this method, the
     * nodesPerInstance map is saved in database.
     * @param instanceId the identifier of the instance
     * @param nodeName the name of the new node that belongs to this instance
     * @param infrastructureId the identifier of the infrastructure
     */
    protected void removeNodeAndTerminateInstanceIfNeeded(final String instanceId, final String nodeName,
            final String infrastructureId) {
        setRuntimeVariable(new RuntimeVariablesHandler<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void handle() {
                // first read from the runtime variables map
                nodesPerInstance = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                // make modifications to the nodesPerInstance map
                nodesPerInstance.get(instanceId).remove(nodeName);
                logger.info("Removed node : " + nodeName);
                if (nodesPerInstance.get(instanceId).isEmpty()) {
                    connectorIaasController.terminateInstance(infrastructureId, instanceId);
                    nodesPerInstance.remove(instanceId);
                    logger.info("Removed instance : " + instanceId);
                }
                // finally write to the runtime variable map
                runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstance);
                return null;
            }
        });
    }

    /**
     * Manage the infrastructure created flag that says whether the
     * infrastructure has already been instantiated. Typically check this flag
     * whenever you would like to create the infrastructure, and set this flag
     * to false whenever the infrastructure is shut down properly.
     * @param expected the value you expect the infrastructureCreatedFlag have
     * @param updated the value you want the infrastructureCreatedFlag to have 
     *                after the check
     * @return whether the comparison went as expected, so it is like whether
     * the infrastructureCreatedFlag was updated
     */
    protected boolean compareAndSetInfrastructureCreatedFlag(final boolean expected, final boolean updated) {
        return setRuntimeVariable(new RuntimeVariablesHandler<Boolean>() {
            @Override
            public Boolean handle() {
                boolean infraCreated = (boolean) runtimeVariables.get(INFRASTRUCTURE_CREATED_FLAG_KEY);
                if (infraCreated == expected) {
                    runtimeVariables.put(INFRASTRUCTURE_CREATED_FLAG_KEY, updated);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    /**
     * Take into account a node in the tracked removed nodes and mark the
     * given instance as free if all the nodes are marked as removed for this
     * instance. This method executes in write lock acquired and persist in
     * database the changed runtime variables at the end.
     */
    private void incrementRemovedNodes(final String nodeName, final String instanceId) {
        setRuntimeVariable(new RuntimeVariablesHandler<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void handle() {
                // first read from the runtime variables map
                nodesPerInstance = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                nbRemovedNodesPerInstance = (Map<String, Integer>) runtimeVariables.get(NB_REMOVED_NODES_PER_INSTANCE_KEY);
                freeInstancesMap = (Map<String, Integer>) runtimeVariables.get(FREE_INSTANCES_MAP_KEY);

                // make modifications to the internal data structures
                if (!nbRemovedNodesPerInstance.containsKey(instanceId)) {
                    nbRemovedNodesPerInstance.put(instanceId, 1);
                } else {
                    int updatedNbRemovedNodes = nbRemovedNodesPerInstance.get(instanceId) + 1;
                    nbRemovedNodesPerInstance.put(instanceId, updatedNbRemovedNodes);
                }
                // after the remove, if the instance pointed to by instanceId
                // has no node left, then the nodesPerInstance map should not
                // contain this instanceId entry anymore
                if (!nodesPerInstance.containsKey(instanceId)) {
                    // we remove the instance from the tracked number of
                    // removed nodes per instance...
                    int nbNodesForInstance = nbRemovedNodesPerInstance.remove(instanceId);
                    // ...and we add the instance to the set of free instances,
                    // with the (total) number of nodes that should be deployed
                    // for this instance. And this will be taken into account
                    // in the next deployment round (this depends on the node
                    // source policy)
                    freeInstancesMap.put(instanceId, nbNodesForInstance);
                }
                logDataStructureContent("Node " + nodeName + " added to the removed nodes set");

                // finally write to the runtime variable map
                runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstance);
                runtimeVariables.put(NB_REMOVED_NODES_PER_INSTANCE_KEY, nbRemovedNodesPerInstance);
                runtimeVariables.put(FREE_INSTANCES_MAP_KEY, freeInstancesMap);
                return null;
            }
        });
    }

    /**
     * Decrement the number of removed nodes and put back the node in the
     * nodesPerInstance map. This method executes in write lock acquired and
     * persist in database the changed runtime variables at the end.
     */
    private void decrementRemovedNodes(final String nodeName, final String instanceId) {
        setRuntimeVariable(new RuntimeVariablesHandler<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void handle() {
                // first read from the runtime variables map
                nodesPerInstance = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                nbRemovedNodesPerInstance = (Map<String, Integer>) runtimeVariables.get(NB_REMOVED_NODES_PER_INSTANCE_KEY);

                // if the instance is not there it means all the nodes have 
                // been down and the instance has been removed (see
                // removeNodeAndTerminateInstanceIfNeeded)
                if (nodesPerInstance.containsKey(instanceId)) {
                    if (nbRemovedNodesPerInstance.containsKey(instanceId)) {
                        int updatedNbRemovedNodes = nbRemovedNodesPerInstance.get(instanceId) - 1;
                        nbRemovedNodesPerInstance.put(instanceId, updatedNbRemovedNodes);
                        nodesPerInstance.get(instanceId).add(nodeName);
                        logDataStructureContent("Node " + nodeName + " removed from the removed nodes set");

                        // finally write to the runtime variable map
                        runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstance);
                        runtimeVariables.put(NB_REMOVED_NODES_PER_INSTANCE_KEY, nbRemovedNodesPerInstance);
                    }
                } else {
                    logger.warn("Down node " + nodeName + " is trying to reconnect, but the instance " + instanceId +
                                " does not exist any more. Instance may be redeployed shortly.");
                }
                return null;
            }
        });
    }

    private void logDataStructureContent(String action) {
        logger.info(action + " - node sets are now: nodes per instance=" + nodesPerInstance +
                    ", number of removed nodes per instance=" + nbRemovedNodesPerInstance + ", free instances map=" +
                    freeInstancesMap);
    }

}
