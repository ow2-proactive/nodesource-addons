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

import java.security.KeyException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeInformation;
import org.objectweb.proactive.core.runtime.ProActiveRuntime;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.resourcemanager.authentication.Client;
import org.ow2.proactive.resourcemanager.db.RMDBManager;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.python.google.common.collect.Sets;


public class OpenstackInfrastructureTest {

    private OpenstackInfrastructure openstackInfrastructure;

    private static final String rmCreds = "UlNBCjEwMjQKUlNBL0VDQi9QS0NTMVBhZGRpbmcKdaUX3K5Cx1epYuylbM3ApIbM0C1gsIZWIX6MsFhzfUZxMnB7/BeUvAFQz3lYcTEqSl2E1LWlibBbxHMCxjUMzSoOZXFKsnTxMCieWetgUcP5sCTO/Kg1UukL4xDqOgpLp1iK0FK4dYDSBBkoUn4ePBLZWu2YOb1+mPFEE2G2hxSW0DUVMXginosmRNcG5P2n1GqrDgplizEjD7G6rN6UezDGXv6MthSjP9VbFAzOSY79UTELjOhb0Rz3qfBhl4DNvae2c3ZrHJkKHL3P6GC4Zz0BvY90VKOMQj8Y8LuwdxKthWDgcmFppfSldJ8vwsEIhbwHM9bzsRCBDelMRyDYOD9km24uOMYGAmv6/EqMHRsC2w7drAhByzU/xg4OGtYaDy4xBzlHGzpq2NBCwTdx+xLiSmTFNT7U/MZ1dTTFmCUfJ25fM5ncO1rPNvLqrzdrm2x2NEhnXCTGO1aFVTUhMyLmeNi/0KmXmE51WHPyeoWxZ5/GfQT9HxUMVBei3tE8gCM6f5W4iNTZKY6Et1nVKw==";

    private static final String STARTUP_SCRIPT = "node download cmd\nnode start cmd";

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

    @Mock
    private Client client = new Client();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        openstackInfrastructure = new OpenstackInfrastructure();
        openstackInfrastructure.setRmDbManager(dbManager);
        openstackInfrastructure.initializePersistedInfraVariables();
    }

    @Test
    public void testInitialParamateres() {
        assertThat(openstackInfrastructure.username, is(nullValue()));
        assertThat(openstackInfrastructure.password, is(nullValue()));
        assertThat(openstackInfrastructure.endpoint, is(nullValue()));
        assertThat(openstackInfrastructure.flavor, is(nullValue()));
        assertThat(openstackInfrastructure.publicKeyName, is(nullValue()));
        assertThat(openstackInfrastructure.rmHostname, is(not(nullValue())));
        assertThat(openstackInfrastructure.connectorIaasURL,
                   is("http://" + openstackInfrastructure.rmHostname + ":8080/connector-iaas"));
        assertThat(openstackInfrastructure.image, is(nullValue()));
        assertThat(openstackInfrastructure.numberOfInstances, is(1));
        assertThat(openstackInfrastructure.numberOfNodesPerInstance, is(1));
        assertThat(openstackInfrastructure.nodeJarURL,
                   is("http://" + openstackInfrastructure.rmHostname + ":8080/rest/node.jar"));
        assertThat(openstackInfrastructure.additionalProperties, is("-Dproactive.useIPaddress=true"));

    }

    @Test
    public void testConfigure() {

        when(nodeSource.getName()).thenReturn("node source name");
        openstackInfrastructure.nodeSource = nodeSource;
        openstackInfrastructure.configure("username",
                                          "password",
                                          "domain",
                                          "endpoint",
                                          "scopePrefix",
                                          "scopeValue",
                                          "region",
                                          "identityVersion",
                                          "openstack-image",
                                          "3",
                                          "network0",
                                          "publicKeyName",
                                          "1",
                                          "1",
                                          "http://localhost:8088/connector-iaas",
                                          "test.activeeon.com",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "-Dnew=value",
                                          240000,
                                          STARTUP_SCRIPT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureNotEnoughParameters() {

        when(nodeSource.getName()).thenReturn("node source name");
        openstackInfrastructure.nodeSource = nodeSource;

        openstackInfrastructure.configure("username",
                                          "password",
                                          "endpoint",
                                          "test.activeeon.com",
                                          "http://localhost:8088/connector-iaas",
                                          "network0",
                                          "publicKeyName",
                                          "2",
                                          "3",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "-Dnew=value");
    }

    @Test
    public void testAcquireNode() throws ScriptNotExecutedException, KeyException {

        when(nodeSource.getName()).thenReturn("node source name");

        when(nodeSource.getAdministrator()).thenReturn(client);

        when(client.getCredentials()).thenReturn(Credentials.getCredentialsBase64(rmCreds.getBytes()));

        openstackInfrastructure.nodeSource = nodeSource;

        openstackInfrastructure.configure("username",
                                          "password",
                                          "domain",
                                          "endpoint",
                                          "scopePrefix",
                                          "scopeValue",
                                          "region",
                                          "identityVersion",
                                          "openstack-image",
                                          "3",
                                          "",
                                          "publicKeyName",
                                          "2",
                                          "1",
                                          "http://localhost:8088/connector-iaas",
                                          "test.activeeon.com",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "-Dnew=value",
                                          240000,
                                          STARTUP_SCRIPT);

        openstackInfrastructure.connectorIaasController = connectorIaasController;

        openstackInfrastructure.setRmUrl("http://test.activeeon.com");

        when(connectorIaasController.createOpenstackInfrastructure("node_source_name",
                                                                   "username",
                                                                   "password",
                                                                   "domain",
                                                                   "scopePrefix",
                                                                   "scopeValue",
                                                                   "region",
                                                                   "identity",
                                                                   "endpoint",
                                                                   false)).thenReturn("node_source_name");

        when(connectorIaasController.createOpenstackInstance(anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyInt(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyList())).thenReturn(Sets.newHashSet("123", "456"));

        openstackInfrastructure.acquireNode();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createOpenstackInfrastructure("node_source_name",
                                                                      "username",
                                                                      "password",
                                                                      "domain",
                                                                      "scopePrefix",
                                                                      "scopeValue",
                                                                      "region",
                                                                      "identityVersion",
                                                                      "endpoint",
                                                                      true);

        verify(connectorIaasController, times(2)).createOpenstackInstance(anyString(),
                                                                          anyString(),
                                                                          anyString(),
                                                                          anyInt(),
                                                                          anyString(),
                                                                          anyString(),
                                                                          anyString(),
                                                                          anyList());

        verify(connectorIaasController, times(0)).executeScript(anyString(), anyString(), anyList());

    }

    @Test
    public void testAcquireAllNodes() throws ScriptNotExecutedException, KeyException {
        when(nodeSource.getName()).thenReturn("node source name");

        when(nodeSource.getAdministrator()).thenReturn(client);

        when(client.getCredentials()).thenReturn(Credentials.getCredentialsBase64(rmCreds.getBytes()));

        openstackInfrastructure.nodeSource = nodeSource;

        openstackInfrastructure.configure("username",
                                          "password",
                                          "domain",
                                          "endpoint",
                                          "scopePrefix",
                                          "scopeValue",
                                          "region",
                                          "identityVersion",
                                          "openstack-image",
                                          "3",
                                          "",
                                          "publicKeyName",
                                          "2",
                                          "1",
                                          "http://localhost:8088/connector-iaas",
                                          "test.activeeon.com",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "-Dnew=value",
                                          240000,
                                          STARTUP_SCRIPT);

        openstackInfrastructure.connectorIaasController = connectorIaasController;

        openstackInfrastructure.setRmUrl("http://test.activeeon.com");

        when(connectorIaasController.createOpenstackInfrastructure("node_source_name",
                                                                   "username",
                                                                   "password",
                                                                   "domain",
                                                                   "scopePrefix",
                                                                   "scopeValue",
                                                                   "region",
                                                                   "identity",
                                                                   "endpoint",
                                                                   false)).thenReturn("node_source_name");

        when(connectorIaasController.createOpenstackInstance(anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyInt(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyList())).thenReturn(Sets.newHashSet("123", "456"));

        openstackInfrastructure.acquireAllNodes();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createOpenstackInfrastructure("node_source_name",
                                                                      "username",
                                                                      "password",
                                                                      "domain",
                                                                      "scopePrefix",
                                                                      "scopeValue",
                                                                      "region",
                                                                      "identityVersion",
                                                                      "endpoint",
                                                                      true);

        verify(connectorIaasController, times(2)).createOpenstackInstance(anyString(),
                                                                          anyString(),
                                                                          anyString(),
                                                                          anyInt(),
                                                                          anyString(),
                                                                          anyString(),
                                                                          anyString(),
                                                                          anyList());

        verify(connectorIaasController, times(0)).executeScript(anyString(), anyString(), anyList());
    }

    @Test
    public void testRemoveNode() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        openstackInfrastructure.nodeSource = nodeSource;

        openstackInfrastructure.configure("username",
                                          "password",
                                          "domain",
                                          "endpoint",
                                          "scopePrefix",
                                          "scopeValue",
                                          "region",
                                          "identityVersion",
                                          "openstack-image",
                                          "3",
                                          "",
                                          "publicKeyName",
                                          "1",
                                          "1",
                                          "http://localhost:8088/connector-iaas",
                                          "test.activeeon.com",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "-Dnew=value",
                                          240000,
                                          STARTUP_SCRIPT);

        openstackInfrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(openstackInfrastructure.getInstanceIdNodeProperty())).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(node.getProActiveRuntime()).thenReturn(proActiveRuntime);

        when(nodeInformation.getName()).thenReturn("nodename");

        openstackInfrastructure.getNodesPerInstancesMap().put("123", Sets.newHashSet("nodename"));

        openstackInfrastructure.removeNode(node);

        verify(proActiveRuntime).killNode("nodename");

        verify(connectorIaasController).terminateInstanceByTag("node_source_name", "123");

        assertThat(openstackInfrastructure.getNodesPerInstancesMap().isEmpty(), is(true));

    }

    @Test
    public void testNotifyAcquiredNode() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("node source name");
        openstackInfrastructure.nodeSource = nodeSource;
        openstackInfrastructure.configure("username",
                                          "password",
                                          "domain",
                                          "endpoint",
                                          "scopePrefix",
                                          "scopeValue",
                                          "region",
                                          "identityVersion",
                                          "openstack-image",
                                          "3",
                                          "",
                                          "publicKeyName",
                                          "1",
                                          "1",
                                          "http://localhost:8088/connector-iaas",
                                          "test.activeeon.com",
                                          "wget -nv test.activeeon.com/rest/node.jar",
                                          "-Dnew=value",
                                          240000,
                                          STARTUP_SCRIPT);

        openstackInfrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(openstackInfrastructure.getInstanceIdNodeProperty())).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(nodeInformation.getName()).thenReturn("nodename");

        openstackInfrastructure.notifyAcquiredNode(node);

        assertThat(openstackInfrastructure.getNodesPerInstancesMapCopy().get("123").isEmpty(), is(false));
        assertThat(openstackInfrastructure.getNodesPerInstancesMapCopy().get("123").size(), is(1));
        assertThat(openstackInfrastructure.getNodesPerInstancesMapCopy().get("123").contains("nodename"), is(true));

    }

    @Test
    public void testGetDescription() {
        assertThat(openstackInfrastructure.getDescription(),
                   is("Handles ProActive nodes using Nova compute service of Openstack Cloud."));
    }

}
