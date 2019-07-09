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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeInformation;
import org.objectweb.proactive.core.runtime.ProActiveRuntime;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.LinuxInitScriptGenerator;
import org.python.google.common.collect.Sets;


public class AWSEC2InfrastructureTest {

    private static final String AWS_KEY = "aws_key";

    private static final String AWS_SECRET_KEY = "aws_secret_key";

    private static final int NUMBER_OF_INSTANCES = 2;

    private static final int NUMBER_OF_NODES_PER_INSTANCE = 3;

    private static final String IMAGE = "region/ami-image";

    private static final String VM_USERNAME = "admin";

    private static final String VM_KEY_PAIR_NAME = "keyname";

    private static final byte[] VM_PRIVATE_KEY = new byte[] { 0, 1, 2, 3, 4 };

    private static final int RAM = 512;

    private static final int CORES = 1;

    private static final String SPOT_PRICE = ""; //"0.05";

    private static final String SECURITY_GROUP_NAMES = "sg-default";

    private static final String SUBNET_ID = "subnet-id";

    private static final String RM_HOSTNAME = "test.activeeon.com";

    private static final String CONNECTOR_IAAS_URL = "http://localhost:8088/connector-iaas";

    private static final String NODE_JAR_URL = "wget -nv test.activeeon.com/rest/node.jar";

    private static final String ADDITIONAL_PROPERTIES = "-Dnew=value";

    private static final int NODE_TIMEOUT = 300000;

    private static final boolean DESTROY_INSTANCES_ON_SHUTDOWN = true;

    private static final String INFRASTRUCTURE_ID = "infrastructure_id";

    private static final List<String> INIT_SCRIPTS = Arrays.asList("node download cmd", "node start cmd");

    @InjectMocks
    @Spy
    private AWSEC2Infrastructure awsec2Infrastructure;

    @Mock
    private ConnectorIaasController connectorIaasController;

    @Mock
    private NodeSource nodeSource;

    @Mock
    private Node node;

    @Mock
    private ProActiveRuntime proActiveRuntime;

    @Mock
    private NodeInformation nodeInformation;

    @Mock
    private LinuxInitScriptGenerator linuxInitScriptGenerator;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        awsec2Infrastructure.initializePersistedInfraVariables();
    }

    @Test
    public void testInitialParamateres() {
        assertThat(awsec2Infrastructure.awsKey, is(nullValue()));
        assertThat(awsec2Infrastructure.awsSecretKey, is(nullValue()));
        assertThat(awsec2Infrastructure.numberOfInstances, is(not(nullValue())));
        assertThat(awsec2Infrastructure.numberOfNodesPerInstance, is(not(nullValue())));
        assertThat(awsec2Infrastructure.image, not(nullValue()));
        assertThat(awsec2Infrastructure.vmUsername, is(not(nullValue())));
        assertThat(awsec2Infrastructure.vmKeyPairName, is(nullValue()));
        assertThat(awsec2Infrastructure.vmPrivateKey, is(nullValue()));
        assertThat(awsec2Infrastructure.ram, is(not(nullValue())));
        assertThat(awsec2Infrastructure.cores, is(not(nullValue())));
        assertThat(awsec2Infrastructure.rmHostname, is(not(nullValue())));
        assertThat(awsec2Infrastructure.connectorIaasURL,
                   is("http://" + awsec2Infrastructure.rmHostname + ":8080/connector-iaas"));
        assertThat(awsec2Infrastructure.nodeJarURL, is(awsec2Infrastructure.rmHostname + ":8080/rest/node.jar"));
        assertThat(awsec2Infrastructure.additionalProperties, is(""));
    }

    @Test
    public void testConfigure() {
        awsec2Infrastructure.configure(AWS_KEY,
                                       AWS_SECRET_KEY,
                                       NUMBER_OF_INSTANCES,
                                       NUMBER_OF_NODES_PER_INSTANCE,
                                       IMAGE,
                                       VM_USERNAME,
                                       VM_KEY_PAIR_NAME,
                                       VM_PRIVATE_KEY,
                                       RAM,
                                       CORES,
                                       //                                       SPOT_PRICE,
                                       SECURITY_GROUP_NAMES,
                                       SUBNET_ID,
                                       RM_HOSTNAME,
                                       CONNECTOR_IAAS_URL,
                                       NODE_JAR_URL,
                                       ADDITIONAL_PROPERTIES,
                                       NODE_TIMEOUT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureNotEnoughParameters() {
        awsec2Infrastructure.configure(AWS_KEY,
                                       AWS_SECRET_KEY,
                                       IMAGE,
                                       VM_USERNAME,
                                       VM_KEY_PAIR_NAME,
                                       VM_PRIVATE_KEY,
                                       RAM,
                                       CORES,
                                       //                                       SPOT_PRICE,
                                       SECURITY_GROUP_NAMES,
                                       SUBNET_ID,
                                       RM_HOSTNAME,
                                       CONNECTOR_IAAS_URL,
                                       NODE_JAR_URL,
                                       ADDITIONAL_PROPERTIES,
                                       NODE_TIMEOUT);
    }

    @Test
    public void testAcquireNode() throws ScriptNotExecutedException {
        awsec2Infrastructure.configure(AWS_KEY,
                                       AWS_SECRET_KEY,
                                       NUMBER_OF_INSTANCES,
                                       NUMBER_OF_NODES_PER_INSTANCE,
                                       IMAGE,
                                       VM_USERNAME,
                                       VM_KEY_PAIR_NAME,
                                       VM_PRIVATE_KEY,
                                       RAM,
                                       CORES,
                                       //                                       SPOT_PRICE,
                                       SECURITY_GROUP_NAMES,
                                       SUBNET_ID,
                                       RM_HOSTNAME,
                                       CONNECTOR_IAAS_URL,
                                       NODE_JAR_URL,
                                       ADDITIONAL_PROPERTIES,
                                       NODE_TIMEOUT);

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(nodeSource.getName()).thenReturn(INFRASTRUCTURE_ID);

        when(linuxInitScriptGenerator.buildScript(anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyInt())).thenReturn(INIT_SCRIPTS);

        doAnswer((Answer<Object>) invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(nodeSource).executeInParallel(any(Runnable.class));

        doReturn(new ArrayList<>()).when(awsec2Infrastructure).addMultipleDeployingNodes(anyListOf(String.class),
                                                                                         anyString(),
                                                                                         anyString(),
                                                                                         anyLong());

        when(connectorIaasController.createInfrastructure(INFRASTRUCTURE_ID,
                                                          AWS_KEY,
                                                          AWS_SECRET_KEY,
                                                          null,
                                                          DESTROY_INSTANCES_ON_SHUTDOWN)).thenReturn(INFRASTRUCTURE_ID);

        when(connectorIaasController.createAwsEc2InstancesWithOptions(INFRASTRUCTURE_ID,
                                                                      INFRASTRUCTURE_ID,
                                                                      IMAGE,
                                                                      1,
                                                                      CORES,
                                                                      RAM,
                                                                      SPOT_PRICE,
                                                                      SECURITY_GROUP_NAMES,
                                                                      SUBNET_ID,
                                                                      null,
                                                                      VM_USERNAME,
                                                                      VM_KEY_PAIR_NAME)).thenReturn(Sets.newHashSet("123"));

        awsec2Infrastructure.acquireNode();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createInfrastructure(INFRASTRUCTURE_ID,
                                                             AWS_KEY,
                                                             AWS_SECRET_KEY,
                                                             null,
                                                             DESTROY_INSTANCES_ON_SHUTDOWN);

        verify(connectorIaasController).createAwsEc2InstancesWithOptions(INFRASTRUCTURE_ID,
                                                                         INFRASTRUCTURE_ID,
                                                                         IMAGE,
                                                                         1,
                                                                         CORES,
                                                                         RAM,
                                                                         SPOT_PRICE,
                                                                         SECURITY_GROUP_NAMES,
                                                                         SUBNET_ID,
                                                                         null,
                                                                         VM_USERNAME,
                                                                         VM_KEY_PAIR_NAME);

        verify(connectorIaasController, times(1)).executeScriptWithKeyAuthentication(anyString(),
                                                                                     anyString(),
                                                                                     anyListOf(String.class),
                                                                                     anyString(),
                                                                                     anyString());

    }

    @Test
    public void testAcquireAllNodes() throws ScriptNotExecutedException {
        awsec2Infrastructure.configure(AWS_KEY,
                                       AWS_SECRET_KEY,
                                       NUMBER_OF_INSTANCES,
                                       NUMBER_OF_NODES_PER_INSTANCE,
                                       IMAGE,
                                       VM_USERNAME,
                                       VM_KEY_PAIR_NAME,
                                       VM_PRIVATE_KEY,
                                       RAM,
                                       CORES,
                                       //                                       SPOT_PRICE,
                                       SECURITY_GROUP_NAMES,
                                       SUBNET_ID,
                                       RM_HOSTNAME,
                                       CONNECTOR_IAAS_URL,
                                       NODE_JAR_URL,
                                       ADDITIONAL_PROPERTIES,
                                       NODE_TIMEOUT);

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(nodeSource.getName()).thenReturn(INFRASTRUCTURE_ID);

        when(linuxInitScriptGenerator.buildScript(anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyString(),
                                                  anyInt())).thenReturn(INIT_SCRIPTS);
        doAnswer((Answer<Object>) invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(nodeSource).executeInParallel(any(Runnable.class));

        doReturn(new ArrayList<>()).when(awsec2Infrastructure).addMultipleDeployingNodes(anyListOf(String.class),
                                                                                         anyString(),
                                                                                         anyString(),
                                                                                         anyLong());

        when(connectorIaasController.createInfrastructure(INFRASTRUCTURE_ID,
                                                          AWS_KEY,
                                                          AWS_SECRET_KEY,
                                                          null,
                                                          DESTROY_INSTANCES_ON_SHUTDOWN)).thenReturn(INFRASTRUCTURE_ID);

        when(connectorIaasController.createAwsEc2InstancesWithOptions(INFRASTRUCTURE_ID,
                                                                      INFRASTRUCTURE_ID,
                                                                      IMAGE,
                                                                      NUMBER_OF_INSTANCES,
                                                                      CORES,
                                                                      RAM,
                                                                      SPOT_PRICE,
                                                                      SECURITY_GROUP_NAMES,
                                                                      SUBNET_ID,
                                                                      null,
                                                                      VM_USERNAME,
                                                                      VM_KEY_PAIR_NAME)).thenReturn(Sets.newHashSet("123",
                                                                                                                    "456"));

        awsec2Infrastructure.acquireAllNodes();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createInfrastructure(INFRASTRUCTURE_ID,
                                                             AWS_KEY,
                                                             AWS_SECRET_KEY,
                                                             null,
                                                             DESTROY_INSTANCES_ON_SHUTDOWN);

        verify(connectorIaasController).createAwsEc2InstancesWithOptions(INFRASTRUCTURE_ID,
                                                                         INFRASTRUCTURE_ID,
                                                                         IMAGE,
                                                                         NUMBER_OF_INSTANCES,
                                                                         CORES,
                                                                         RAM,
                                                                         SPOT_PRICE,
                                                                         SECURITY_GROUP_NAMES,
                                                                         SUBNET_ID,
                                                                         null,
                                                                         VM_USERNAME,
                                                                         VM_KEY_PAIR_NAME);

        verify(connectorIaasController, times(2)).executeScriptWithKeyAuthentication(anyString(),
                                                                                     anyString(),
                                                                                     anyListOf(String.class),
                                                                                     anyString(),
                                                                                     anyString());
    }

    @Test
    public void testRemoveNode() throws ProActiveException, RMException {
        final String instanceId = "instance-id";
        final String nodeName = "region__" + instanceId + "_0";
        final String instanceIdWithRegion = "region/" + instanceId;

        awsec2Infrastructure.configure(AWS_KEY,
                                       AWS_SECRET_KEY,
                                       NUMBER_OF_INSTANCES,
                                       NUMBER_OF_NODES_PER_INSTANCE,
                                       IMAGE,
                                       VM_USERNAME,
                                       VM_KEY_PAIR_NAME,
                                       VM_PRIVATE_KEY,
                                       RAM,
                                       CORES,
                                       //                                       SPOT_PRICE,
                                       SECURITY_GROUP_NAMES,
                                       SUBNET_ID,
                                       RM_HOSTNAME,
                                       CONNECTOR_IAAS_URL,
                                       NODE_JAR_URL,
                                       ADDITIONAL_PROPERTIES,
                                       NODE_TIMEOUT);

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(nodeSource.getName()).thenReturn(INFRASTRUCTURE_ID);

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(node.getProActiveRuntime()).thenReturn(proActiveRuntime);

        when(nodeInformation.getName()).thenReturn(nodeName);

        awsec2Infrastructure.getNodesPerInstancesMap().put(instanceIdWithRegion, Sets.newHashSet(nodeName));

        awsec2Infrastructure.removeNode(node);

        verify(proActiveRuntime).killNode(nodeName);

        verify(connectorIaasController).terminateInstance(INFRASTRUCTURE_ID, instanceIdWithRegion);

        assertThat(awsec2Infrastructure.getNodesPerInstancesMap().isEmpty(), is(true));

    }

    @Test
    public void testNotifyAcquiredNode() throws ProActiveException, RMException {

        awsec2Infrastructure.configure(AWS_KEY,
                                       AWS_SECRET_KEY,
                                       NUMBER_OF_INSTANCES,
                                       NUMBER_OF_NODES_PER_INSTANCE,
                                       IMAGE,
                                       VM_USERNAME,
                                       VM_KEY_PAIR_NAME,
                                       VM_PRIVATE_KEY,
                                       RAM,
                                       CORES,
                                       //                                       SPOT_PRICE,
                                       SECURITY_GROUP_NAMES,
                                       SUBNET_ID,
                                       RM_HOSTNAME,
                                       CONNECTOR_IAAS_URL,
                                       NODE_JAR_URL,
                                       ADDITIONAL_PROPERTIES,
                                       NODE_TIMEOUT);

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(awsec2Infrastructure.getInstanceIdNodeProperty())).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(nodeInformation.getName()).thenReturn("nodename");

        awsec2Infrastructure.notifyAcquiredNode(node);

        assertThat(awsec2Infrastructure.getNodesPerInstancesMapCopy().get("123").isEmpty(), is(false));
        assertThat(awsec2Infrastructure.getNodesPerInstancesMapCopy().get("123").size(), is(1));
        assertThat(awsec2Infrastructure.getNodesPerInstancesMapCopy().get("123").contains("nodename"), is(true));

    }

    @Test
    public void testGetDescription() {
        assertThat(awsec2Infrastructure.getDescription(),
                   is("Handles nodes from the Amazon Elastic Compute Cloud Service."));
    }

}
