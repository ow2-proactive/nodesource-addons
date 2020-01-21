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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class AzureBillingRateCard {

    private static final Logger LOGGER = Logger.getLogger(AzureBillingRateCard.class);

    private HashMap<String, Double> meterRates = null;

    public AzureBillingRateCard() {
        LOGGER.debug("AzureBillingRateCard constructor");
        this.meterRates = new HashMap<String, Double>();
    }

    private String queryRateCard(String subscriptionId, String accessToken) throws IOException {

        String endpoint = String.format("https://management.azure.com/subscriptions/%s/providers/Microsoft.Commerce/RateCard?api-version=%s&$filter=OfferDurableId eq '%s' and Currency eq '%s' and Locale eq '%s' and RegionInfo eq '%s'",
                                        subscriptionId,
                                        "2016-08-31-preview",
                                        "MS-AZR-0003p",
                                        "USD",
                                        "en-US",
                                        "US")
                                .replaceAll(" ", "%20");

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.addRequestProperty("Authorization", "Bearer " + accessToken);
        conn.addRequestProperty("Content-Type", "application/json");
        conn.connect();

        // getInputStream() works only if Http returns a code between 200 and 299
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getResponseCode() / 100 == 2
                                                                                                           ? conn.getInputStream()
                                                                                                           : conn.getErrorStream(),
                                                                         "UTF-8"));

        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    String getRateCard(String subscriptionId, AzureBillingCredentials azureBillingCredentials)
            throws IOException, AzureBillingException {

        // Get a new rate card
        String rateCard = queryRateCard(subscriptionId, azureBillingCredentials.renewOrOnlyGetAccessToken(false));

        // Renew the access token if required
        JsonObject jsonObject = new JsonParser().parse(new String(rateCard)).getAsJsonObject();
        if (jsonObject.has("error")) {
            String queryErrorCodeMessage = new JsonParser().parse(new String(rateCard))
                                                           .getAsJsonObject()
                                                           .get("error")
                                                           .getAsJsonObject()
                                                           .get("code")
                                                           .getAsString();

            if (queryErrorCodeMessage.equals("ExpiredAuthenticationToken")) {
                LOGGER.debug("AzureBillingRateCard getRateCard ExpiredAuthenticationToken");

                azureBillingCredentials.renewOrOnlyGetAccessToken(true);
                getRateCard(subscriptionId, azureBillingCredentials);
            } else {
                LOGGER.debug("AzureBillingRateCard getRateCard AzureBillingException " + queryErrorCodeMessage);

                throw new AzureBillingException(queryErrorCodeMessage);
            }
        }
        LOGGER.debug("AzureBillingRateCard getRateCard rateCard is retrieved");

        return rateCard;
    }

    public void updateVmRates(String subscriptionId, AzureBillingCredentials azureBillingCredentials)
            throws IOException, AzureBillingException {

        LOGGER.debug("AzureBillingRateCard updateVmRates");

        // Get a new rate card
        String rateCardJson = getRateCard(subscriptionId, azureBillingCredentials);

        // Parse the json rate card
        Iterator<JsonElement> rateIterator = new JsonParser().parse(rateCardJson)
                                                             .getAsJsonObject()
                                                             .get("Meters")
                                                             .getAsJsonArray()
                                                             .iterator();

        // Update the vm meter rates map
        while (rateIterator.hasNext()) {
            JsonObject rate = rateIterator.next().getAsJsonObject();

            // Only consider VM rates
            if (rate.get("MeterCategory").getAsString().equals("Virtual Machines")) {
                // Get the meter id
                String meterId = rate.get("MeterId").getAsString();
                // Get the meter rate
                JsonObject meterRates = rate.get("MeterRates").getAsJsonObject();

                if (meterRates.entrySet().size() != 1) {
                    LOGGER.debug("AzureBillingRateCard updateVmRates AzureBillingException meterRates size() != 1");
                    throw new AzureBillingException("Multiple meter rates not supported for resources cost estimation");
                }

                double meterRate = meterRates.get("0").getAsDouble();
                this.meterRates.put(meterId, meterRate);
            }
        }
    }

    public void setMeterRates(HashMap<String, Double> meterRates) {
        this.meterRates = new HashMap<>(meterRates);
    }

    public HashMap<String, Double> getMeterRates() {
        return this.meterRates;
    }

    public double getMeterRate(String meterId) {
        return this.meterRates.get(meterId);
    }

}
