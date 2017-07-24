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
 * @author ActiveEon Team
 * @since 21/07/17
 */
public abstract class AbstractAddonInfrastructure extends InfrastructureManager {

    private static final Logger logger = Logger.getLogger(AbstractAddonInfrastructure.class);

    /**
     * Key to retrieve the nodesPerInstance map within the runtime variables
     * map of the infrastructure, which holds the variables that are saved in
     * database.
     */
    private static final String NODES_PER_INSTANCES_KEY = "nodesPerInstances";

    /**
     * The controller is transient as it is not supposed to be serialized or
     * saved in database. It should be recreated at start up.
     */
    protected transient ConnectorIaasController connectorIaasController = null;

    /**
     * Information about instances and their nodes. Maps the instance
     * identifier to the name of the nodes that belong to it.
     */
    protected Map<String, Set<String>> nodesPerInstances;

    /**
     * Default constructor
     */
    protected AbstractAddonInfrastructure() {
        nodesPerInstances = new HashMap<>();
    }

    @Override
    public void notifyDownNode(String nodeName, String nodeUrl, Node node) throws RMException {
        removeNode(node);
    }

    @Override
    protected void initializeRuntimeVariables() {
        runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstances);
    }

    /**
     * This method puts a new node name entry for the given instance. It does
     * that within a write lock acquired, that is exposed by the super class.
     * The hook at the end of this method ensures that the nodesPerInstance
     * map is save in database.
     * @param instanceId the identifier of the instance
     * @param nodeName the name of the new node that belongs to this instance
     */
    protected void addNewNodeForInstance(final String instanceId, final String nodeName) {
        setRuntimeVariable(new RuntimeVariablesHandler<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void handle() {
                // first read from the runtime variables map
                nodesPerInstances = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                // make modifications to the nodesPerInstance map
                if (!nodesPerInstances.containsKey(instanceId)) {
                    nodesPerInstances.put(instanceId, new HashSet<String>());
                }
                nodesPerInstances.get(instanceId).add(nodeName);
                // finally write to the runtime variable map
                runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstances);
                return null;
            }
        });
    }

    /**
     * This method removes a node name entry for the given instance, and call
     * the instance termination mechanism if there no more nodes attached to
     * this instance. It does all that within a write lock acquired, that is
     * exposed by the super class. The hook at the end of this method ensures
     * that the nodesPerInstance map is save in database.
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
                nodesPerInstances = (Map<String, Set<String>>) runtimeVariables.get(NODES_PER_INSTANCES_KEY);
                // make modifications to the nodesPerInstance map
                nodesPerInstances.get(instanceId).remove(nodeName);
                logger.info("Removed node : " + nodeName);
                if (nodesPerInstances.get(instanceId).isEmpty()) {
                    connectorIaasController.terminateInstance(infrastructureId, instanceId);
                    nodesPerInstances.remove(instanceId);
                    logger.info("Removed instance : " + instanceId);
                }
                // finally write to the runtime variable map
                runtimeVariables.put(NODES_PER_INSTANCES_KEY, nodesPerInstances);
                return null;
            }
        });
    }
}
