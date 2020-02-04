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


public class AzureBillingRateCardTest {

    private static final Logger LOGGER = Logger.getLogger(AzureBillingRateCardTest.class);

    private static final JsonParser JSON_PARSER = new JsonParser();

    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();

    private final String subscriptionId = "cdd4aa9d-1927-42f2-aea3-3b52122c1b5f";

    private final String offerId = "MS-AZR-0003p";

    private final String currency = "USD";

    private final String locale = "en-US";

    private final String regionInfo = "US";

    private AzureBillingRateCard azureBillingRateCard = null;

    private AzureBillingCredentials azureBillingCredentials = null;

    @Before
    public void init() throws IOException {
        this.azureBillingRateCard = new AzureBillingRateCard(this.subscriptionId,
                                                             this.offerId,
                                                             this.currency,
                                                             this.locale,
                                                             this.regionInfo);
        this.azureBillingCredentials = new AzureBillingCredentials("4665a602-72aa-4f8b-b7c6-279b2cb88ba7",
                                                                   "d8f5e423-7970-412c-a1ae-f76e405ba980",
                                                                   "4cfebd9c-cad1-4285-8b66-e4736311004d");
    }

    @Test
    public void testGetRateCard() throws Exception {
        try {

            String rateCardJson = this.azureBillingRateCard.getRateCard(this.azureBillingCredentials);

            Gson gson = GSON_BUILDER.setPrettyPrinting().create();
            JsonElement je = JSON_PARSER.parse(rateCardJson);
            String prettyJsonString = gson.toJson(je);
            LOGGER.debug(prettyJsonString);

            boolean rateCardReceived = JSON_PARSER.parse(rateCardJson).getAsJsonObject().has("Meters");
            Assert.assertTrue("Succeeded in retrieving the rate card", rateCardReceived);

        } catch (IOException e) {
            Assert.assertTrue("Failed to get the rate card", false);
        }
    }
}
