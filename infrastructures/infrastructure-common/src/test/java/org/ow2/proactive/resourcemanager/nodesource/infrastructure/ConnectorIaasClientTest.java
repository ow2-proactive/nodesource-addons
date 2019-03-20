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
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.Sets;


public class ConnectorIaasClientTest {

    private ConnectorIaasClient connectorIaasClient;

    private final String infrastructureJson = "{id=\"123\"}";

    @Mock
    private RestClient restClient;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(restClient.getInfrastructures()).thenReturn("[]");
        connectorIaasClient = new ConnectorIaasClient(restClient);

    }

    @Test
    public void testCreateInfrastructure() {

        Mockito.when(restClient.postInfrastructures(infrastructureJson))
               .thenReturn("{'infrastructureId' : 'infra123'}");
        connectorIaasClient.createInfrastructure("infra123", infrastructureJson);

        InOrder inOrderRestClient = Mockito.inOrder(restClient);

        inOrderRestClient.verify(restClient).deleteInfrastructure("infra123", false);
        inOrderRestClient.verify(restClient).postInfrastructures(infrastructureJson);
    }

    @Test
    public void testCreateInstances() {

        Mockito.when(restClient.postInstances("infra123", "{id=\"123\",tag=\"instanceTag123\"}"))
               .thenReturn("[{'id' : 'instance123'}]");

        Set<String> instances = connectorIaasClient.createInstancesIfNotExist("infra123",
                                                                              "instanceTag123",
                                                                              "{id=\"123\",tag=\"instanceTag123\"}",
                                                                              Sets.<JSONObject> newHashSet());

        assertThat(instances.size(), is(1));

        assertThat(instances.iterator().next(), is("instance123"));

    }

    @Test
    public void testCreateMultipleInstances() {

        Mockito.when(restClient.postInstances("infra123", "{id=\"123\",tag=\"instanceTag123\"}"))
               .thenReturn("[{'id' : 'instance123'},{'id' : 'instance456'},{'id' : 'instance789'}]");

        Set<String> instances = connectorIaasClient.createInstancesIfNotExist("infra123",
                                                                              "instanceTag123",
                                                                              "{id=\"123\",tag=\"instanceTag123\"}",
                                                                              Sets.<JSONObject> newHashSet());

        assertThat(instances.size(), is(3));

        Set<String> expectedId = Sets.newHashSet("instance123", "instance456", "instance789");

        for (String id : instances) {
            assertThat(expectedId.remove(id), is(true));
        }

    }

    @Test
    public void testTerminateInstance() {

        connectorIaasClient.terminateInstance("infra123", "123456");

        Mockito.verify(restClient).deleteInstance("infra123", "instanceId", "123456");

    }

    @Test
    public void testTerminateInstanceByTag() {

        connectorIaasClient.terminateInstanceByTag("infra123", "123456");

        Mockito.verify(restClient).deleteInstance("infra123", "instanceTag", "123456");

    }

    @Test
    public void testTerminateInfrastructure() {

        connectorIaasClient.terminateInfrastructure("infra123", false);

        Mockito.verify(restClient).deleteInfrastructure("infra123", false);

    }

    @Test
    public void testRunScriptOnInstance() {

        connectorIaasClient.runScriptOnInstance("infra123", "123456", "somescriptjason");

        Mockito.verify(restClient).postScript("infra123", "instanceId", "123456", "somescriptjason");

    }

}
