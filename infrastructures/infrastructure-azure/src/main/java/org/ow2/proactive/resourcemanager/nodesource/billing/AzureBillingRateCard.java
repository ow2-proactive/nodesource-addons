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
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class AzureBillingRateCard {

    private static final Logger LOGGER = Logger.getLogger(AzureBillingRateCard.class);

    private static final JsonParser JSON_PARSER = new JsonParser();

    private String subscriptionId;

    private String offerId;

    private String currency;

    private String locale;

    private String regionInfo;

    private HashMap<String, Double> meterRates;

    public AzureBillingRateCard(String subscriptionId, String offerId, String currency, String locale,
            String regionInfo) {
        LOGGER.debug("AzureBillingRateCard constructor");
        this.subscriptionId = subscriptionId;
        this.offerId = offerId;
        this.currency = currency;
        this.locale = locale;
        this.regionInfo = regionInfo;
        this.meterRates = new HashMap<>();
    }

    private String queryRateCard(String accessToken) throws IOException {

        String endpoint = String.format("https://management.azure.com/subscriptions/%s/providers/Microsoft.Commerce/RateCard?api-version=%s&$filter=OfferDurableId eq '%s' and Currency eq '%s' and Locale eq '%s' and RegionInfo eq '%s'",
                                        this.subscriptionId,
                                        "2016-08-31-preview",
                                        this.offerId,
                                        this.currency,
                                        this.locale,
                                        this.regionInfo)
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

    String getRateCard(AzureBillingCredentials azureBillingCredentials) throws IOException, AzureBillingException {

        // Get a new rate card
        String queryResult = queryRateCard(azureBillingCredentials.renewOrOnlyGetAccessToken(false));

        JsonObject jsonObject = JSON_PARSER.parse(queryResult).getAsJsonObject();
        if (jsonObject.has("Meters")) {
            LOGGER.debug("AzureBillingRateCard getRateCard rateCard is retrieved");
            return queryResult;
        } else if (jsonObject.has("error") &&
                   jsonObject.get("error")
                             .getAsJsonObject()
                             .get("code")
                             .getAsString()
                             .equals("ExpiredAuthenticationToken")) {
            LOGGER.debug("AzureBillingRateCard getRateCard ExpiredAuthenticationToken, renewing it.");
            azureBillingCredentials.renewOrOnlyGetAccessToken(true);
            getRateCard(azureBillingCredentials);
        } else {
            LOGGER.error("AzureBillingRateCard getRateCard AzureBillingException " + queryResult);
            throw new AzureBillingException(queryResult);
        }
        return null;
    }

    public void updateVmRates(AzureBillingCredentials azureBillingCredentials)
            throws IOException, AzureBillingException {

        LOGGER.debug("AzureBillingRateCard updateVmRates");

        // Get a new rate card
        String rateCardJson = getRateCard(azureBillingCredentials);

        // Parse the json rate card
        Iterator<JsonElement> rateIterator = JSON_PARSER.parse(rateCardJson)
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

    public double getMeterRate(String meterId) {
        return this.meterRates.get(meterId);
    }

}
