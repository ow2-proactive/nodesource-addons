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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Assert;
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


public class AzureInfrastructureTest {

    private AzureInfrastructure azureInfrastructure;

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
        azureInfrastructure = new AzureInfrastructure();
        azureInfrastructure.setRmDbManager(dbManager);
        azureInfrastructure.initializeRuntimeVariables();
    }

    @Test
    public void testDefaultValuesOfAllParameters() {
        assertThat(azureInfrastructure.clientId, is(nullValue()));
        assertThat(azureInfrastructure.secret, is(nullValue()));
        assertThat(azureInfrastructure.domain, is(nullValue()));
        assertThat(azureInfrastructure.subscriptionId, is(nullValue()));
        assertThat(azureInfrastructure.authenticationEndpoint, is(nullValue()));
        assertThat(azureInfrastructure.managementEndpoint, is(nullValue()));
        assertThat(azureInfrastructure.resourceManagerEndpoint, is(nullValue()));
        assertThat(azureInfrastructure.graphEndpoint, is(nullValue()));
        assertThat(azureInfrastructure.rmHostname, is(not(nullValue())));
        assertThat(azureInfrastructure.connectorIaasURL,
                   is("http://" + azureInfrastructure.rmHostname + ":8080/connector-iaas"));
        assertThat(azureInfrastructure.image, is(nullValue()));
        assertThat(azureInfrastructure.vmSizeType, is(nullValue()));
        assertThat(azureInfrastructure.vmUsername, is(nullValue()));
        assertThat(azureInfrastructure.vmPassword, is(nullValue()));
        assertThat(azureInfrastructure.vmPublicKey, is(nullValue()));
        assertThat(azureInfrastructure.resourceGroup, is(nullValue()));
        assertThat(azureInfrastructure.region, is(nullValue()));
        assertThat(azureInfrastructure.numberOfInstances, is(1));
        assertThat(azureInfrastructure.numberOfNodesPerInstance, is(1));
        if (System.getProperty("os.name").contains("Windows")) {
            assertThat(azureInfrastructure.downloadCommand,
                       is("powershell -command \"& { (New-Object Net.WebClient).DownloadFile('http://" +
                          azureInfrastructure.rmHostname + ":8080/rest/node.jar', 'node.jar') }\""));
        } else {
            assertThat(azureInfrastructure.downloadCommand,
                       is("wget -nv http://" + azureInfrastructure.rmHostname + ":8080/rest/node.jar"));

        }
        assertThat(azureInfrastructure.privateNetworkCIDR, is(nullValue()));
        assertThat(azureInfrastructure.staticPublicIP, is(true));
        assertThat(azureInfrastructure.additionalProperties, is("-Dproactive.useIPaddress=true"));
    }

    @Test
    public void testConfigureDoNotThrowIllegalArgumentExceptionWithValidParameters() {
        when(nodeSource.getName()).thenReturn("Node source Name");
        azureInfrastructure.nodeSource = nodeSource;

        try {
            azureInfrastructure.configure("clientId",
                                          "secret",
                                          "domain",
                                          "subscriptionId",
                                          "authenticationEndpoint",
                                          "managementEndpoint",
                                          "resourceManagerEndpoint",
                                          "graphEndpoint",
                                          "test.activeeon.com",
                                          "http://localhost:8088/connector-iaas",
                                          "image",
                                          "Standard_D1_v2",
                                          "vmUsername",
                                          "vmPassword",
                                          "vmPublicKey",
                                          "resourceGroup",
                                          "region",
                                          "2",
                                          "3",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "192.168.1.0/24",
                                          true,
                                          "-Dnew=value");
            Assert.assertTrue(Boolean.TRUE);
        } catch (IllegalArgumentException e) {
            fail("NPE not thrown");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureNotEnoughParameters() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        azureInfrastructure.nodeSource = nodeSource;

        azureInfrastructure.configure("clientId", "secret");
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureWithANullArgument() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        azureInfrastructure.nodeSource = nodeSource;

        azureInfrastructure.configure("clientId",
                                      "secret",
                                      "domain",
                                      "subscriptionId",
                                      "authenticationEndpoint",
                                      "managementEndpoint",
                                      "resourceManagerEndpoint",
                                      "graphEndpoint",
                                      "test.activeeon.com",
                                      "http://localhost:8088/connector-iaas",
                                      null,
                                      "Standard_D1_v2",
                                      "vmUsername",
                                      "vmPassword",
                                      "vmPublicKey",
                                      "resourceGroup",
                                      "region",
                                      "2",
                                      "3",
                                      "wget -nv test.activeeon.com/rest/node.jar",
                                      "192.168.1.0/24",
                                      true,
                                      "-Dnew=value");
    }

    @Test
    public void testAcquiringTwoNodesByRegisteringInfrastructureCreatingInstancesAndInjectingScriptOnThem()
            throws ScriptNotExecutedException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        azureInfrastructure.nodeSource = nodeSource;

        azureInfrastructure.configure("clientId",
                                      "secret",
                                      "domain",
                                      "subscriptionId",
                                      "authenticationEndpoint",
                                      "managementEndpoint",
                                      "resourceManagerEndpoint",
                                      "graphEndpoint",
                                      "test.activeeon.com",
                                      "http://localhost:8088/connector-iaas",
                                      "image",
                                      "Standard_D1_v2",
                                      "vmUsername",
                                      "vmPassword",
                                      "vmPublicKey",
                                      "resourceGroup",
                                      "region",
                                      "2",
                                      "3",
                                      "wget -nv test.activeeon.com/rest/node.jar",
                                      "192.168.1.0/24",
                                      true,
                                      "-Dnew=value");

        azureInfrastructure.connectorIaasController = connectorIaasController;

        azureInfrastructure.setRmUrl("http://test.activeeon.com");

        when(connectorIaasController.createAzureInfrastructure("node_source_name",
                                                               "clientId",
                                                               "secret",
                                                               "domain",
                                                               "subscriptionId",
                                                               "authenticationEndpoint",
                                                               "managementEndpoint",
                                                               "resourceManagerEndpoint",
                                                               "graphEndpoint",
                                                               false)).thenReturn("node_source_name");

        when(connectorIaasController.createAzureInstances("node_source_name",
                                                          "node_source_name",
                                                          "image",
                                                          2,
                                                          "vmUsername",
                                                          "vmPassword",
                                                          "vmPublicKey",
                                                          "Standard_D1_v2",
                                                          "resourceGroup",
                                                          "region",
                                                          "192.168.1.0/24",
                                                          true)).thenReturn(Sets.newHashSet("123", "456"));

        azureInfrastructure.acquireNode();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createAzureInfrastructure("node_source_name",
                                                                  "clientId",
                                                                  "secret",
                                                                  "domain",
                                                                  "subscriptionId",
                                                                  "authenticationEndpoint",
                                                                  "managementEndpoint",
                                                                  "resourceManagerEndpoint",
                                                                  "graphEndpoint",
                                                                  false);

        verify(connectorIaasController).createAzureInstances("node_source_name",
                                                             "node_source_name",
                                                             "image",
                                                             2,
                                                             "vmUsername",
                                                             "vmPassword",
                                                             "vmPublicKey",
                                                             "Standard_D1_v2",
                                                             "resourceGroup",
                                                             "region",
                                                             "192.168.1.0/24",
                                                             true);

        verify(connectorIaasController, times(2)).executeScript(anyString(), anyString(), anyList());
    }

    @Test
    public void testRemoveNode() throws ProActiveException, RMException {
        when(nodeSource.getName()).thenReturn("Node source Name");
        azureInfrastructure.nodeSource = nodeSource;

        azureInfrastructure.configure("clientId",
                                      "secret",
                                      "domain",
                                      "subscriptionId",
                                      "authenticationEndpoint",
                                      "managementEndpoint",
                                      "resourceManagerEndpoint",
                                      "graphEndpoint",
                                      "test.activeeon.com",
                                      "http://localhost:8088/connector-iaas",
                                      "image",
                                      "Standard_D1_v2",
                                      "vmUsername",
                                      "vmPassword",
                                      "vmPublicKey",
                                      "resourceGroup",
                                      "region",
                                      "2",
                                      "3",
                                      "wget -nv test.activeeon.com/rest/node.jar",
                                      "192.168.1.0/24",
                                      true,
                                      "-Dnew=value");

        azureInfrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(AzureInfrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(node.getProActiveRuntime()).thenReturn(proActiveRuntime);

        when(nodeInformation.getName()).thenReturn("nodename");

        azureInfrastructure.getNodesPerInstancesMap().put("123", Sets.newHashSet("nodename"));

        azureInfrastructure.removeNode(node);

        verify(proActiveRuntime).killNode("nodename");

        verify(connectorIaasController).terminateInstance("node_source_name", "123");

        assertThat(azureInfrastructure.getNodesPerInstancesMap().isEmpty(), is(true));

    }

    @Test
    public void testThatNotifyAcquiredNodeMethodFillsTheNodesMapCorrectly() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        azureInfrastructure.nodeSource = nodeSource;
        azureInfrastructure.configure("clientId",
                                      "secret",
                                      "domain",
                                      "subscriptionId",
                                      "authenticationEndpoint",
                                      "managementEndpoint",
                                      "resourceManagerEndpoint",
                                      "graphEndpoint",
                                      "test.activeeon.com",
                                      "http://localhost:8088/connector-iaas",
                                      "image",
                                      "Standard_D1_v2",
                                      "vmUsername",
                                      "vmPassword",
                                      "vmPublicKey",
                                      "resourceGroup",
                                      "region",
                                      "2",
                                      "3",
                                      "wget -nv test.activeeon.com/rest/node.jar",
                                      "192.168.1.0/24",
                                      true,
                                      "-Dnew=value");

        azureInfrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(AzureInfrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(nodeInformation.getName()).thenReturn("nodename");

        azureInfrastructure.notifyAcquiredNode(node);

        assertThat(azureInfrastructure.getNodesPerInstancesMapCopy().get("123").isEmpty(), is(false));
        assertThat(azureInfrastructure.getNodesPerInstancesMapCopy().get("123").size(), is(1));
        assertThat(azureInfrastructure.getNodesPerInstancesMapCopy().get("123").contains("nodename"), is(true));
    }
}
