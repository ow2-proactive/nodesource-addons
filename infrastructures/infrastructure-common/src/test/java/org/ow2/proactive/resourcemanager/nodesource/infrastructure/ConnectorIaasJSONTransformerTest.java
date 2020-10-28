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

import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

import com.google.common.collect.Lists;


public class ConnectorIaasJSONTransformerTest {

    @Test
    public void testGetInfrastructureJSON() {

        JSONObject actual = new JSONObject(ConnectorIaasJSONTransformer.getInfrastructureJSONWithEndPoint("infrastructureId",
                                                                                                          "type",
                                                                                                          "username",
                                                                                                          "password",
                                                                                                          null,
                                                                                                          false));

        assertThat(actual.getString("id"), is("infrastructureId"));
        assertThat(actual.getString("type"), is("type"));
        assertThat(actual.getJSONObject("credentials").getString("username"), is("username"));
        assertThat(actual.getJSONObject("credentials").getString("password"), is("password"));
    }

    @Test
    public void testGetInstanceJSON() {
        JSONObject actual = new JSONObject(ConnectorIaasJSONTransformer.getInstanceJSON("tag",
                                                                                        "image",
                                                                                        "number",
                                                                                        "minCores",
                                                                                        "minRam",
                                                                                        null,
                                                                                        null,
                                                                                        null,
                                                                                        null));

        assertThat(actual.getString("tag"), is("tag"));
        assertThat(actual.getString("image"), is("image"));
        assertThat(actual.getString("number"), is("number"));
        assertThat(actual.getJSONObject("hardware").getString("minCores"), is("minCores"));
        assertThat(actual.getJSONObject("hardware").getString("minRam"), is("minRam"));
        assertThat(actual.getJSONObject("options").toString(), is("{}"));
    }

    @Test
    public void testGetInstanceJSONWithSpotPrice() {
        JSONObject actual = new JSONObject(ConnectorIaasJSONTransformer.getInstanceJSON("tag",
                                                                                        "image",
                                                                                        "number",
                                                                                        "minCores",
                                                                                        "minRam",
                                                                                        "0.05",
                                                                                        "default",
                                                                                        "127.0.0.1",
                                                                                        "00:50:56:11:11:11"));

        assertThat(actual.getString("tag"), is("tag"));
        assertThat(actual.getString("image"), is("image"));
        assertThat(actual.getString("number"), is("number"));
        assertThat(actual.getJSONObject("hardware").getString("minCores"), is("minCores"));
        assertThat(actual.getJSONObject("hardware").getString("minRam"), is("minRam"));
        assertThat(actual.getJSONObject("options").getString("spotPrice"), is("0.05"));
        assertThat(actual.getJSONObject("options").getJSONArray("macAddresses").toString(),
                   is("[\"00:50:56:11:11:11\"]"));
    }

    @Test
    public void testGetScriptInstanceJSON() {
        List<String> scripts = Lists.newArrayList("ls", "wget url");
        assertThat(ConnectorIaasJSONTransformer.getScriptInstanceJSONWithCredentials(scripts, null, null),
                   is("{\"scripts\":[\"ls\",\"wget url\"]}"));
    }

    @Test
    public void testGetInfrastructureJSONWithEndpoint() {

        JSONObject actual = new JSONObject(ConnectorIaasJSONTransformer.getInfrastructureJSONWithEndPoint("infrastructureId",
                                                                                                          "type",
                                                                                                          "username",
                                                                                                          "password",
                                                                                                          "endpoint",
                                                                                                          true));

        assertThat(actual.getString("id"), is("infrastructureId"));
        assertThat(actual.getString("type"), is("type"));
        assertThat(actual.getString("endpoint"), is("endpoint"));
        assertThat(actual.getBoolean("toBeRemovedOnShutdown"), is(true));
        assertThat(actual.getJSONObject("credentials").getString("username"), is("username"));
        assertThat(actual.getJSONObject("credentials").getString("password"), is("password"));
    }

    @Test
    public void testGetInstanceJSONWithPublicKeyAndScripts() {

        List<String> scripts = null;
        JSONObject actual = new JSONObject(ConnectorIaasJSONTransformer.getOpenstackInstanceJSON("tag",
                                                                                                 "image",
                                                                                                 "number",
                                                                                                 "publicKeyName",
                                                                                                 "type",
                                                                                                 "",
                                                                                                 scripts));

        assertThat(actual.getString("tag"), is("tag"));
        assertThat(actual.getString("image"), is("image"));
        assertThat(actual.getString("number"), is("number"));
        assertThat(actual.getJSONObject("credentials").getString("publicKeyName"), is("publicKeyName"));
        assertThat(actual.getJSONObject("hardware").getString("type"), is("type"));
        assertThat(actual.getJSONObject("initScript").getJSONArray("scripts").length(), is(0));
    }

    @Test
    public void testGetGceInstanceJSON() {
        final String tag = "tag";
        final String number = "5";
        final String vmUsername = "username";
        final String vmPublicKey = "publicKey";
        final String vmPrivateKey = "privateKey";
        final List<String> initScripts = Arrays.asList("cmd 1", "cmd 2");
        final String image = "image";
        final String region = "region";
        final String ram = "1024";
        final String cores = "2";

        JSONObject actual = new JSONObject(ConnectorIaasJSONTransformer.getGceInstanceJSON(tag,
                                                                                           number,
                                                                                           vmUsername,
                                                                                           vmPublicKey,
                                                                                           vmPrivateKey,
                                                                                           initScripts,
                                                                                           image,
                                                                                           region,
                                                                                           ram,
                                                                                           cores));

        assertThat(actual.getString("tag"), is(tag));
        assertThat(actual.getString("number"), is(number));
        assertThat(actual.getString("image"), is(image));
        assertThat(actual.getJSONObject("credentials").getString("username"), is(vmUsername));
        assertThat(actual.getJSONObject("credentials").getString("publicKey"), is(vmPublicKey));
        assertThat(actual.getJSONObject("credentials").getString("privateKey"), is(vmPrivateKey));
        assertThat(actual.getJSONObject("initScript").getJSONArray("scripts").length(), is(2));
        assertThat(actual.getJSONObject("initScript").getJSONArray("scripts").getString(0), is("cmd 1"));
        assertThat(actual.getJSONObject("initScript").getJSONArray("scripts").getString(1), is("cmd 2"));
        assertThat(actual.getJSONObject("options").getString("region"), is(region));
        assertThat(actual.getJSONObject("hardware").getString("minRam"), is(ram));
        assertThat(actual.getJSONObject("hardware").getString("minCores"), is(cores));
    }
}
