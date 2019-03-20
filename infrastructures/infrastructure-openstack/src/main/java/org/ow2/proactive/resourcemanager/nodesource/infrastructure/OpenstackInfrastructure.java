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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.LinuxInitScriptGenerator;

import com.google.common.collect.Maps;


public class OpenstackInfrastructure extends AbstractAddonInfrastructure {

    public static final String INSTANCE_TAG_NODE_PROPERTY = "instanceTag";

    public static final String INFRASTRUCTURE_TYPE = "openstack-nova";

    private static final Logger logger = Logger.getLogger(OpenstackInfrastructure.class);

    private transient LinuxInitScriptGenerator linuxInitScriptGenerator = new LinuxInitScriptGenerator();

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

    @Configurable(description = "Total instances to create")
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = "http://" + generateDefaultRMHostname() + ":8080/connector-iaas";

    @Configurable(description = "Resource Manager hostname or ip address")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Command used to download the node jar")
    protected String downloadCommand = linuxInitScriptGenerator.generateNodeDownloadCommand(rmHostname);

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    protected String additionalProperties = "-Dproactive.useIPaddress=true";

    @Override
    public void configure(Object... parameters) {

        logger.info("Validating parameters : " + parameters);
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

        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);

    }

    private void validate(Object[] parameters) {

        if (parameters == null || parameters.length < 15) {
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

        for (int i = 1; i <= numberOfInstances; i++) {

            String instanceTag = getInfrastructureId() + "_" + i;

            List<String> scripts = linuxInitScriptGenerator.buildScript(instanceTag,
                                                                        getRmUrl(),
                                                                        rmHostname,
                                                                        INSTANCE_TAG_NODE_PROPERTY,
                                                                        additionalProperties,
                                                                        nodeSource.getName(),
                                                                        numberOfNodesPerInstance);

            connectorIaasController.createOpenstackInstance(getInfrastructureId(),
                                                            instanceTag,
                                                            image,
                                                            1,
                                                            flavor,
                                                            publicKeyName,
                                                            scripts);
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
        return "Handles nodes using Nova compute service of Openstack Cloud.";
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
            // first read from the runtime variables map
            nodesPerInstance = (Map<String, Set<String>>) persistedInfraVariables.get(NODES_PER_INSTANCES_KEY);
            // make modifications to the nodesPerInstance map
            if (nodesPerInstance.get(instanceTag) != null) {
                nodesPerInstance.get(instanceTag).remove(nodeName);
                logger.info("Removed node : " + nodeName);
                if (nodesPerInstance.get(instanceTag).isEmpty()) {
                    if (terminateInstanceIfEmpty) {
                        connectorIaasController.terminateInstanceByTag(infrastructureId, instanceTag);
                        logger.info("Instance terminated: " + instanceTag);
                    }
                    nodesPerInstance.remove(instanceTag);
                    logger.info("Removed instance : " + instanceTag);
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
}
