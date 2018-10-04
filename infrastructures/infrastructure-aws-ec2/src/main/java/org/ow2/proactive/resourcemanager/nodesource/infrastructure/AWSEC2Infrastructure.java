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
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.python.google.common.collect.Sets;

import com.google.common.collect.Lists;


public class AWSEC2Infrastructure extends AbstractAddonInfrastructure {

    public static final String INSTANCE_ID_NODE_PROPERTY = "instanceId";

    public static final String INFRASTRUCTURE_TYPE = "aws-ec2";

    private static final Logger logger = Logger.getLogger(AWSEC2Infrastructure.class);

    @Configurable(description = "The AWS_AKEY")
    protected String aws_key = null;

    @Configurable(description = "The AWS_SKEY")
    protected String aws_secret_key = null;

    @Configurable(description = "Resource manager hostname or ip address")
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL")
    protected String connectorIaasURL = "http://" + generateDefaultRMHostname() + ":8080/connector-iaas";

    @Configurable(description = "Image")
    protected String image = null;

    @Configurable(description = "The virtual machine Username (optional)")
    protected String vmUsername = null;

    @Configurable(description = "The name of the AWS key pair (optional)")
    protected String vmKeyPairName = null;

    @Configurable(fileBrowser = true, description = "The AWS private key file (optional)")
    protected byte[] vmPrivateKey;

    @Configurable(description = "Total instance to create")
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance")
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "Command used to download the worker jar")
    protected String downloadCommand = generateDefaultDownloadCommand();

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")")
    protected String additionalProperties = "";

    @Configurable(description = "minumum RAM required (in Mega Bytes)")
    protected int ram = 512;

    @Configurable(description = "minimum number of CPU cores required")
    protected int cores = 1;

    @Configurable(description = "Spot Price")
    protected String spotPrice = null;

    @Configurable(description = "Security Group Names")
    protected String securityGroupNames = null;

    @Configurable(description = "Subnet and VPC")
    protected String subnetId = null;

    /**
     * Key to retrieve the key pair used to deploy the infrastructure
     */
    private static final String KEY_PAIR_KEY = "keyPair";

    @Override
    public void configure(Object... parameters) {

        logger.info("Validating parameters : " + parameters);
        validate(parameters);

        int parameterIndex = 0;
        this.aws_key = parameters[parameterIndex++].toString().trim();
        this.aws_secret_key = parameters[parameterIndex++].toString().trim();
        this.rmHostname = parameters[parameterIndex++].toString().trim();
        this.connectorIaasURL = parameters[parameterIndex++].toString().trim();
        this.image = parameters[parameterIndex++].toString().trim();
        this.vmUsername = parameters[parameterIndex++].toString().trim();
        this.vmKeyPairName = parameters[parameterIndex++].toString().trim();
        this.vmPrivateKey = (byte[]) parameters[parameterIndex++];
        this.numberOfInstances = Integer.parseInt(parameters[parameterIndex++].toString().trim());
        this.numberOfNodesPerInstance = Integer.parseInt(parameters[parameterIndex++].toString().trim());
        this.downloadCommand = parameters[parameterIndex++].toString().trim();
        this.additionalProperties = parameters[parameterIndex++].toString().trim();
        this.ram = Integer.parseInt(parameters[parameterIndex++].toString().trim());
        this.cores = Integer.parseInt(parameters[parameterIndex++].toString().trim());
        this.spotPrice = parameters[parameterIndex++].toString().trim();
        this.securityGroupNames = parameters[parameterIndex++].toString().trim();
        this.subnetId = parameters[parameterIndex].toString().trim();

        connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);

    }

    private void validate(Object[] parameters) {
        int parameterIndex = 0;
        if (parameters == null || parameters.length < 14) {
            throw new IllegalArgumentException("Invalid parameters for EC2Infrastructure creation");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("EC2 key must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("EC2 secret key  must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The Resource manager hostname must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The connector-iaas URL must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The image id must be specified");
        }

        // VM username
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex++] = "";
        }

        // key pair name
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex++] = "";
        }

        // private key file
        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex++] = "";
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The number of instances to create must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The number of nodes per instance to deploy must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The download node.jar command must be specified");
        }

        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex++] = "";
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The amount of minimum RAM required must be specified");
        }

        if (parameters[parameterIndex++] == null) {
            throw new IllegalArgumentException("The minimum number of cores required must be specified");
        }

        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex++] = "";
        }

        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex++] = "";
        }

        if (parameters[parameterIndex] == null) {
            parameters[parameterIndex] = "";
        }

    }

    @Override
    public void acquireNode() {

        connectorIaasController.waitForConnectorIaasToBeUP();

        connectorIaasController.createInfrastructure(getInfrastructureId(),
                                                     aws_key,
                                                     aws_secret_key,
                                                     null,
                                                     RM_CLOUD_INFRASTRUCTURES_DESTROY_INSTANCES_ON_SHUTDOWN.getValueAsBoolean());

        String instanceTag = getInfrastructureId();
        Set<String> instancesIds = Sets.newHashSet();
        boolean existPersistedInstanceIds = false;

        // we check a persisted flag that says whether this infrastructure has
        // already been deployed
        if (expectInstancesAlreadyCreated(false, true)) {

            // by default, the key pair that is used to deploy the instances has
            // the name of the node source
            String keyPairName = createOrUseKeyPair(getInfrastructureId());

            // create instances
            if (spotPrice.isEmpty() && securityGroupNames.isEmpty() && subnetId.isEmpty()) {
                instancesIds = connectorIaasController.createAwsEc2Instances(getInfrastructureId(),
                                                                             instanceTag,
                                                                             image,
                                                                             numberOfInstances,
                                                                             cores,
                                                                             ram,
                                                                             vmUsername,
                                                                             keyPairName);
            } else {
                instancesIds = connectorIaasController.createAwsEc2InstancesWithOptions(getInfrastructureId(),
                                                                                        instanceTag,
                                                                                        image,
                                                                                        numberOfInstances,
                                                                                        cores,
                                                                                        ram,
                                                                                        spotPrice,
                                                                                        securityGroupNames,
                                                                                        subnetId,
                                                                                        null,
                                                                                        vmUsername,
                                                                                        keyPairName);
            }
            logger.info("Instances ids created: " + instancesIds);

        } else {

            // if the infrastructure was already created, then we need to
            // look at the free instances, if any (the ones on which no node
            // run. In the current implementation, this can only happen when
            // nodes are down. Indeed if they are all removed on purpose, the
            // instance should be shut down). Note that in this case, if the
            // free instances map is empty, no script will be run at all.
            Map<String, Integer> freeInstancesMap = getInstancesWithoutNodesMapCopy();
            instancesIds = freeInstancesMap.keySet();
            logger.info("Instances ids previously saved which require script re-execution: " + instancesIds);
            existPersistedInstanceIds = true;
        }

        // execute script on instances to deploy or redeploy nodes on them
        for (String currentInstanceId : instancesIds) {
            List<String> scripts = Lists.newArrayList(this.downloadCommand,
                                                      "nohup " + generateDefaultStartNodeCommand(currentInstanceId) +
                                                                            "  &");
            try {
                connectorIaasController.executeScriptWithKeyAuthentication(getInfrastructureId(),
                                                                           currentInstanceId,
                                                                           scripts,
                                                                           vmUsername,
                                                                           getPersistedKeyPairInfo().getValue());
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

    private String createOrUseKeyPair(String infrastructureId) {
        SimpleImmutableEntry<String, String> keyPairInfo;
        if (vmPrivateKey.length == 0 || vmKeyPairName.isEmpty()) {
            // create a key pair in AWS
            try {
                logger.info("Creating an AWS key pair");
                keyPairInfo = connectorIaasController.createAwsEc2KeyPair(infrastructureId,
                                                                          infrastructureId,
                                                                          image,
                                                                          numberOfInstances,
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
        return "Handles nodes from the Amazon Elastic Compute Cloud Service.";
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

    private String generateDefaultDownloadCommand() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "powershell -command \"& { (New-Object Net.WebClient).DownloadFile('" + this.rmHostname +
                   ":8080/rest/node.jar" + "', 'node.jar') }\"";
        } else {
            return "wget -nv " + this.rmHostname + ":8080/rest/node.jar";
        }
    }

    private String generateDefaultStartNodeCommand(String instanceId) {
        try {
            String rmUrlToUse = getRmUrl();

            String protocol = rmUrlToUse.substring(0, rmUrlToUse.indexOf(':')).trim();
            return "java -jar node.jar -Dproactive.communication.protocol=" + protocol +
                   " -Dproactive.pamr.router.address=" + rmHostname + " -D" + INSTANCE_ID_NODE_PROPERTY + "=" +
                   instanceId + " " + additionalProperties + " -r " + rmUrlToUse + " -s " + nodeSource.getName() +
                   " -w " + numberOfNodesPerInstance;
        } catch (Exception e) {
            logger.error("Exception when generating the command, fallback on default value", e);
            return "java -jar node.jar -D" + INSTANCE_ID_NODE_PROPERTY + "=" + instanceId + " " + additionalProperties +
                   " -r " + getRmUrl() + " -s " + nodeSource.getName() + " -w " + numberOfNodesPerInstance;
        }
    }

    @Override
    protected String getInstanceIdProperty(Node node) throws RMException {
        try {
            return node.getProperty(INSTANCE_ID_NODE_PROPERTY);
        } catch (ProActiveException e) {
            throw new RMException(e);
        }
    }

    private void persistKeyPairInfo(final SimpleImmutableEntry<String, String> keyPair) {
        setPersistedInfraVariable(new PersistedInfraVariablesHandler<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void handle() {
                // first read from the runtime variables map
                persistedInfraVariables.put(KEY_PAIR_KEY, keyPair);
                return null;
            }
        });
    }

    private SimpleImmutableEntry<String, String> getPersistedKeyPairInfo() {
        return getPersistedInfraVariable(new PersistedInfraVariablesHandler<SimpleImmutableEntry<String, String>>() {
            @Override
            @SuppressWarnings("unchecked")
            public SimpleImmutableEntry<String, String> handle() {
                // first read from the runtime variables map
                return (SimpleImmutableEntry<String, String>) persistedInfraVariables.get(KEY_PAIR_KEY);
            }
        });
    }

}
