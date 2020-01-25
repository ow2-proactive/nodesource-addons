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
package org.ow2.proactive.resourcemanager.nodesource.billing;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;


public class AzureBillingResourceUsageTest {

    private static final Logger LOGGER = Logger.getLogger(AzureBillingResourceUsage.class);

    private static final GsonBuilder gsonBuilder = new GsonBuilder();

    private String subscriptionId = null;

    private AzureBillingResourceUsage azureBillingResourceUsage = null;

    private AzureBillingCredentials azureBillingCredentials = null;

    @Before
    public void init() throws IOException {
        this.subscriptionId = "cdd4aa9d-1927-42f2-aea3-3b52122c1b5f";
        this.azureBillingResourceUsage = new AzureBillingResourceUsage(this.subscriptionId,
                                                                       "ACTIVEEON-DEV",
                                                                       "azurestatic");
        this.azureBillingCredentials = new AzureBillingCredentials("4665a602-72aa-4f8b-b7c6-279b2cb88ba7",
                                                                   "d8f5e423-7970-412c-a1ae-f76e405ba980",
                                                                   "4cfebd9c-cad1-4285-8b66-e4736311004d");
        ;
    }

    @Test
    public void testGetLastResourceUsageHistory() throws AzureBillingException {
        try {

            String resourceUsageHistoryJson = this.azureBillingResourceUsage.getLastResourceUsageHistory(this.subscriptionId,
                                                                                                         this.azureBillingCredentials);

            boolean resourceUsageHistoryReceived = new JsonParser().parse(resourceUsageHistoryJson)
                                                                   .getAsJsonObject()
                                                                   .has("value");

            Assert.assertTrue("Succeeded in retrieving the resource usage history", resourceUsageHistoryReceived);

            Gson gson = gsonBuilder.setPrettyPrinting().create();
            com.google.gson.JsonParser jp = new JsonParser();
            JsonElement je = jp.parse(resourceUsageHistoryJson);
            String prettyJsonString = gson.toJson(je);
            LOGGER.debug(prettyJsonString);

        } catch (IOException e) {
            Assert.assertTrue("Failed to get the resource usage history", false);
        }
    }
}
