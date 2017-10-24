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
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.python.google.common.collect.Sets;

import com.google.common.collect.Lists;


public class MaasInfrastructureTest {

    private MaasInfrastructure maasInfrastructure;

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

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        maasInfrastructure = new MaasInfrastructure();
    }

    @Test
    public void testDefaultValuesOfAllParameters() {
        assertThat(maasInfrastructure.endpoint, is(nullValue()));
        assertThat(maasInfrastructure.apiToken, is(nullValue()));
        assertThat(maasInfrastructure.rmHostname, is(not(nullValue())));
        assertThat(maasInfrastructure.connectorIaasURL,
                   is("http://" + maasInfrastructure.rmHostname + ":8080/connector-iaas"));
        assertThat(maasInfrastructure.image, is(nullValue()));
        assertThat(maasInfrastructure.systemId, is(nullValue()));
        assertThat(maasInfrastructure.vmMinMem, is(nullValue()));
        assertThat(maasInfrastructure.vmMinCpu, is(nullValue()));
        assertThat(maasInfrastructure.ignoreCertificateCheck, is(false));
        assertThat(maasInfrastructure.numberOfInstances, is(1));
        assertThat(maasInfrastructure.numberOfNodesPerInstance, is(1));
        if (System.getProperty("os.name").contains("Windows")) {
            assertThat(maasInfrastructure.downloadCommand,
                       is("powershell -command \"& { (New-Object Net.WebClient).DownloadFile('http://" +
                          maasInfrastructure.rmHostname + ":8080/rest/node.jar', 'node.jar') }\""));
        } else {
            assertThat(maasInfrastructure.downloadCommand,
                       is("wget -nv http://" + maasInfrastructure.rmHostname + ":8080/rest/node.jar"));

        }
        assertThat(maasInfrastructure.additionalProperties, is("-Dproactive.useIPaddress=true"));
    }

    @Test
    public void testConfigureDoNotThrowIllegalArgumentExceptionWithValidParameters() {
        when(nodeSource.getName()).thenReturn("Node source Name");
        maasInfrastructure.nodeSource = nodeSource;

        try {
            maasInfrastructure.configure("apiToken",
                                         "endpoint",
                                         "test.activeeon.com",
                                         "http://localhost:8088/connector-iaas",
                                         "image",
                                         "systemId",
                                         "minCpu",
                                         "minMem",
                                         "2",
                                         "3",
                                         "wget -nv test.activeeon.com/rest/node.jar",
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
        maasInfrastructure.nodeSource = nodeSource;

        maasInfrastructure.configure("apiToken", "really:secret:information");
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureWithANullArgument() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        maasInfrastructure.nodeSource = nodeSource;
        maasInfrastructure.configure(null,
                                     "endpoint",
                                     "test.activeeon.com",
                                     "http://localhost:8088/connector-iaas",
                                     "image",
                                     "systemId",
                                     "minCpu",
                                     "minMem",
                                     "2",
                                     "3",
                                     "wget -nv test.activeeon.com/rest/node.jar",
                                     true,
                                     "-Dnew=value");
    }

    @Test
    public void testAcquiringTwoNodesByRegisteringInfrastructureCreatingInstancesAndInjectingScriptOnThem() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        maasInfrastructure.nodeSource = nodeSource;

        maasInfrastructure.configure("apiToken",
                                     "endpoint",
                                     "test.activeeon.com",
                                     "http://localhost:8088/connector-iaas",
                                     "image",
                                     "systemId",
                                     "minCpu",
                                     "minMem",
                                     "2",
                                     "3",
                                     "wget -nv test.activeeon.com/rest/node.jar",
                                     false,
                                     "-Dnew=value");

        maasInfrastructure.connectorIaasController = connectorIaasController;

        maasInfrastructure.rmUrl = "http://test.activeeon.com";

        when(connectorIaasController.createMaasInfrastructure("node_source_name",
                                                              "apiToken",
                                                              "endpoint",
                                                              false,
                                                              false)).thenReturn("node_source_name");

        when(connectorIaasController.createMaasInstances("node_source_name",
                                                         "node_source_name",
                                                         "image",
                                                         2,
                                                         "systemId",
                                                         "minCpu",
                                                         "minMem",
                                                         Lists.newArrayList("/bin/bash -c 'wget -nv test.activeeon.com/rest/node.jar; nohup java -jar node.jar -Dproactive.communication.protocol=http -Dproactive.pamr.router.address=test.activeeon.com -DinstanceId=systemId -Dnew=value -r http://test.activeeon.com -s Node source Name -w 3 &'"))).thenReturn(Sets.newHashSet("123",
                                                                                                                                                                                                                                                                                                                                                                                    "456"));

        maasInfrastructure.acquireNode();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createMaasInfrastructure("node_source_name",
                                                                 "apiToken",
                                                                 "endpoint",
                                                                 false,
                                                                 false);

        verify(connectorIaasController).createMaasInstances("node_source_name",
                                                            "node_source_name",
                                                            "image",
                                                            2,
                                                            "systemId",
                                                            "minCpu",
                                                            "minMem",
                                                            Lists.newArrayList("/bin/bash -c 'wget -nv test.activeeon.com/rest/node.jar; nohup java -jar node.jar -Dproactive.communication.protocol=http -Dproactive.pamr.router.address=test.activeeon.com -DinstanceId=systemId -Dnew=value -r http://test.activeeon.com -s Node source Name -w 3 &'"));
    }

    @Test
    public void testRemoveNode() throws ProActiveException, RMException {
        when(nodeSource.getName()).thenReturn("Node source Name");
        maasInfrastructure.nodeSource = nodeSource;

        maasInfrastructure.configure("apiToken",
                                     "endpoint",
                                     "test.activeeon.com",
                                     "http://localhost:8088/connector-iaas",
                                     "image",
                                     "systemId",
                                     "minCpu",
                                     "minMem",
                                     "2",
                                     "3",
                                     "wget -nv test.activeeon.com/rest/node.jar",
                                     true,
                                     "-Dnew=value");

        maasInfrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(MaasInfrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(node.getProActiveRuntime()).thenReturn(proActiveRuntime);

        when(nodeInformation.getName()).thenReturn("nodename");

        maasInfrastructure.nodesPerInstances.put("123", Sets.newHashSet("nodename"));

        maasInfrastructure.removeNode(node);

        verify(proActiveRuntime).killNode("nodename");

        verify(connectorIaasController).terminateInstance("node_source_name", "123");

        assertThat(maasInfrastructure.nodesPerInstances.isEmpty(), is(true));

    }

    @Test
    public void testThatNotifyAcquiredNodeMethodFillsTheNodesMapCorrectly() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        maasInfrastructure.nodeSource = nodeSource;
        maasInfrastructure.configure("apiToken",
                                     "endpoint",
                                     "test.activeeon.com",
                                     "http://localhost:8088/connector-iaas",
                                     "image",
                                     "systemId",
                                     "minCpu",
                                     "minMem",
                                     "2",
                                     "3",
                                     "wget -nv test.activeeon.com/rest/node.jar",
                                     true,
                                     "-Dnew=value");

        maasInfrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(MaasInfrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(nodeInformation.getName()).thenReturn("nodename");

        maasInfrastructure.notifyAcquiredNode(node);

        assertThat(maasInfrastructure.nodesPerInstances.get("123").isEmpty(), is(false));
        assertThat(maasInfrastructure.nodesPerInstances.get("123").size(), is(1));
        assertThat(maasInfrastructure.nodesPerInstances.get("123").contains("nodename"), is(true));
    }
}
