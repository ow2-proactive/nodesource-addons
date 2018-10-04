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
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.AbstractMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeInformation;
import org.objectweb.proactive.core.runtime.ProActiveRuntime;
import org.ow2.proactive.resourcemanager.db.RMDBManager;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.python.google.common.collect.Sets;


public class AWSEC2InfrastructureTest {

    private static final byte[] PRIVATE_KEY = new byte[] { 0, 1, 2, 3, 4 };

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
    private RMDBManager dbManager;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        awsec2Infrastructure = new AWSEC2Infrastructure();
        awsec2Infrastructure.setRmDbManager(dbManager);
        awsec2Infrastructure.initializePersistedInfraVariables();
        when(connectorIaasController.createAwsEc2KeyPair(anyString(),
                                                         anyString(),
                                                         anyString(),
                                                         anyInt(),
                                                         anyInt(),
                                                         anyInt())).thenReturn(new AbstractMap.SimpleImmutableEntry<>("keyname",
                                                                                                                      "privatekey"));
    }

    @Test
    public void testInitialParamateres() {
        assertThat(awsec2Infrastructure.aws_key, is(nullValue()));
        assertThat(awsec2Infrastructure.aws_secret_key, is(nullValue()));
        assertThat(awsec2Infrastructure.rmHostname, is(not(nullValue())));
        assertThat(awsec2Infrastructure.connectorIaasURL,
                   is("http://" + awsec2Infrastructure.rmHostname + ":8080/connector-iaas"));
        assertThat(awsec2Infrastructure.image, is(nullValue()));
        assertThat(awsec2Infrastructure.numberOfInstances, is(1));
        assertThat(awsec2Infrastructure.numberOfNodesPerInstance, is(1));
        if (System.getProperty("os.name").contains("Windows")) {
            assertThat(awsec2Infrastructure.downloadCommand,
                       is("powershell -command \"& { (New-Object Net.WebClient).DownloadFile('" +
                          awsec2Infrastructure.rmHostname + ":8080/rest/node.jar', 'node.jar') }\""));
        } else {
            assertThat(awsec2Infrastructure.downloadCommand,
                       is("wget -nv " + awsec2Infrastructure.rmHostname + ":8080/rest/node.jar"));

        }
        assertThat(awsec2Infrastructure.additionalProperties, is(""));
        assertThat(awsec2Infrastructure.ram, is(512));
        assertThat(awsec2Infrastructure.cores, is(1));
    }

    @Test
    public void testConfigure() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key",
                                       "aws_secret_key",
                                       "test.activeeon.com",
                                       "http://localhost:8088/connector-iaas",
                                       "aws-image",
                                       "admin",
                                       "keyname",
                                       PRIVATE_KEY,
                                       "2",
                                       "3",
                                       "wget -nv test.activeeon.com/rest/node.jar",
                                       "-Dnew=value",
                                       512,
                                       1,
                                       "0.05",
                                       "default",
                                       "127.0.0.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureNotEnoughParameters() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key",
                                       "aws_secret_key",
                                       "test.activeeon.com",
                                       "http://localhost:8088/connector-iaas",
                                       "aws-image",
                                       "wget -nv test.activeeon.com/rest/node.jar",
                                       "-Dnew=value",
                                       512,
                                       1,
                                       "0.05",
                                       "default",
                                       "127.0.0.1");
    }

    @Test
    public void testAcquireNode() throws ScriptNotExecutedException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key",
                                       "aws_secret_key",
                                       "test.activeeon.com",
                                       "http://localhost:8088/connector-iaas",
                                       "aws-image",
                                       "admin",
                                       "keyname",
                                       PRIVATE_KEY,
                                       "2",
                                       "3",
                                       "wget -nv test.activeeon.com/rest/node.jar",
                                       "-Dnew=value",
                                       512,
                                       1,
                                       "0.05",
                                       "default",
                                       "127.0.0.1");

        awsec2Infrastructure.connectorIaasController = connectorIaasController;
        awsec2Infrastructure.nodeSource = nodeSource;
        awsec2Infrastructure.setRmUrl("http://test.activeeon.com");

        when(connectorIaasController.createInfrastructure("node_source_name",
                                                          "aws_key",
                                                          "aws_secret_key",
                                                          null,
                                                          true)).thenReturn("node_source_name");

        when(connectorIaasController.createAwsEc2InstancesWithOptions("node_source_name",
                                                                      "node_source_name",
                                                                      "aws-image",
                                                                      2,
                                                                      1,
                                                                      512,
                                                                      "0.05",
                                                                      "default",
                                                                      "127.0.0.1",
                                                                      null,
                                                                      "admin",
                                                                      "keyname")).thenReturn(Sets.newHashSet("123",
                                                                                                             "456"));

        awsec2Infrastructure.acquireNode();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createInfrastructure("node_source_name",
                                                             "aws_key",
                                                             "aws_secret_key",
                                                             null,
                                                             false);

        verify(connectorIaasController).createAwsEc2InstancesWithOptions("node_source_name",
                                                                         "node_source_name",
                                                                         "aws-image",
                                                                         2,
                                                                         1,
                                                                         512,
                                                                         "0.05",
                                                                         "default",
                                                                         "127.0.0.1",
                                                                         null,
                                                                         "admin",
                                                                         "keyname");

        verify(connectorIaasController, times(2)).executeScriptWithKeyAuthentication(anyString(),
                                                                                     anyString(),
                                                                                     anyList(),
                                                                                     anyString(),
                                                                                     anyString());

    }

    @Test
    public void testAcquireAllNodes() throws ScriptNotExecutedException {
        testAcquireNode();
    }

    @Test
    public void testRemoveNode() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key",
                                       "aws_secret_key",
                                       "test.activeeon.com",
                                       "http://localhost:8088/connector-iaas",
                                       "aws-image",
                                       "admin",
                                       "keyname",
                                       PRIVATE_KEY,
                                       "2",
                                       "3",
                                       "wget -nv test.activeeon.com/rest/node.jar",
                                       "-Dnew=value",
                                       512,
                                       1,
                                       "0.05",
                                       "default",
                                       "127.0.0.1");

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(AWSEC2Infrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(node.getProActiveRuntime()).thenReturn(proActiveRuntime);

        when(nodeInformation.getName()).thenReturn("nodename");

        awsec2Infrastructure.getNodesPerInstancesMap().put("123", Sets.newHashSet("nodename"));

        awsec2Infrastructure.removeNode(node);

        verify(proActiveRuntime).killNode("nodename");

        verify(connectorIaasController).terminateInstance("node_source_name", "123");

        assertThat(awsec2Infrastructure.getNodesPerInstancesMap().isEmpty(), is(true));

    }

    @Test
    public void testNotifyAcquiredNode() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key",
                                       "aws_secret_key",
                                       "test.activeeon.com",
                                       "http://localhost:8088/connector-iaas",
                                       "aws-image",
                                       "admin",
                                       "keyname",
                                       PRIVATE_KEY,
                                       "2",
                                       "3",
                                       "wget -nv test.activeeon.com/rest/node.jar",
                                       "-Dnew=value",
                                       512,
                                       1,
                                       "0.05",
                                       "default",
                                       "127.0.0.1");

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(AWSEC2Infrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

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
