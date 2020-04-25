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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.apache.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils;

import lombok.Getter;
import lombok.Setter;


public class AzureBillingResourceUsage {

    private static final Logger LOGGER = Logger.getLogger(AzureBillingResourceUsage.class);

    private static final JsonParser JSON_PARSER = new JsonParser();

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                                                                        .withZone(ZoneOffset.UTC);

    private String subscriptionId;

    @Getter
    @Setter
    private LocalDateTime resourceUsageReportedStartDateTime = null;

    @Getter
    @Setter
    private LocalDateTime resourceUsageReportedEndDateTime = null;

    @Getter
    @Setter
    private double globalCost = 0;

    @Setter
    private String currency;

    @Setter
    private double budget;

    @Getter
    @Setter
    private double budgetPercentage;

    private String resourceUriRegex;

    private HashSet<String> metersIds = null;

    public AzureBillingResourceUsage(String subscriptionId, String resourceGroup, String nodeSourceName,
            String currency, double budget) {

        this.subscriptionId = subscriptionId;
        this.currency = currency;
        this.budget = budget;
        this.metersIds = new HashSet<>();

        // Since ResourceUtils.constructResourceId returns 'resourcegroups' against 'resourcesGroups' in the query result
        // we replace "resourcegroups" by "resourceGroups"
        this.resourceUriRegex = ResourceUtils.constructResourceId(subscriptionId,
                                                                  "(?i)" + resourceGroup, // Ignore case
                                                                  "Microsoft.Compute",
                                                                  ".*", // Any resource type (vm, disk,..)
                                                                  "(?i)" + nodeSourceName + "[0-9]*(?:-[a-zA-Z0-9]+)?", // "<node source name><instance id>" followed (optional) by "-ipJHSdj82sd" for ip, disk, ...
                                                                  "")
                                             .replaceFirst("resourcegroups", "resourceGroups"); // bug in Azure API

        LOGGER.debug("AzureBillingResourceUsage AzureBillingResourceUsage " + this.resourceUriRegex);
    }

    private String queryResourceUsageHistory(String reportedStartTime, String reportedEndTime, String accessToken)
            throws IOException {

        String endpoint = String.format("https://management.azure.com/subscriptions/%s/providers/Microsoft.Commerce/UsageAggregates?api-version=%s&reportedStartTime=%s&reportedEndTime=%s&aggregationGranularity=%s&showDetails=%s",
                                        this.subscriptionId,
                                        "2015-06-01-preview",
                                        reportedStartTime,
                                        reportedEndTime,
                                        "Hourly",
                                        "true")
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

    String getLastResourceUsageHistory(AzureBillingCredentials azureBillingCredentials)
            throws IOException, AzureBillingException {

        // With hourly granularity Azure only accept date times with '00' set to minutes and seconds (i.e. truncated)
        LocalDateTime nowTruncatedLastHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

        // [resourceUsageReportedStartDateTime, resourceUsageReportedEndDateTime] is the global period over which we estimate the resource usage cost.
        // 1) resourceUsageReportedStartDateTime is set only once.
        // 2) resourceUsageReportedEndDateTime is set to the max endDateTime found which returns an history.
        if (this.resourceUsageReportedStartDateTime == null) {
            this.resourceUsageReportedStartDateTime = nowTruncatedLastHour;
        }

        // [startDateTime, endDateTime] are the query parameters to get the resource usage history (periodically called).
        // 1) startDateTime is set to the previous period end date time (resourceUsageReportedEndDateTime) which returned an history.
        // 2) endDateTime is set to the current truncated hour and is decreased 1 hour by 1 hour until the query returns an history (Azure does not accept too recent end date time).
        LocalDateTime startDateTime;
        if (this.resourceUsageReportedEndDateTime == null) {
            startDateTime = this.resourceUsageReportedStartDateTime;
        } else {
            startDateTime = this.resourceUsageReportedEndDateTime;
        }
        LocalDateTime endDateTime = nowTruncatedLastHour;

        // Find the max endDateTime with available resource usage history
        String lastResourceUsageHistory = null;
        // while startDateTime < endDateTime
        while (startDateTime.isBefore(endDateTime)) {

            // Query the resource usage history over the current period
            String startDateTimeStr = formatter.format(startDateTime);
            String endDateTimeStr = formatter.format(endDateTime);
            lastResourceUsageHistory = queryResourceUsageHistory(startDateTimeStr,
                                                                 endDateTimeStr,
                                                                 azureBillingCredentials.renewOrOnlyGetAccessToken(false));
            LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory considering [" + startDateTimeStr +
                         ";" + endDateTimeStr + "] = " + lastResourceUsageHistory);

            JsonObject jsonObject = JSON_PARSER.parse(lastResourceUsageHistory).getAsJsonObject();

            // HISTORY RETRIEVED !!
            if (JSON_PARSER.parse(lastResourceUsageHistory).getAsJsonObject().has("value")) {
                this.resourceUsageReportedEndDateTime = endDateTime;
                LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory resource usage history is finally retrieved!");
                return lastResourceUsageHistory;

            } else if (jsonObject.has("error")) { // HISTORY NOT RETRIEVED BUT TRY AGAIN !!

                JsonObject queryErrorObject = jsonObject.get("error").getAsJsonObject();
                String queryErrorCode = queryErrorObject.get("code").getAsString();
                String queryErrorMessage = queryErrorObject.get("message").getAsString();

                if (queryErrorCode.equals("ExpiredAuthenticationToken")) {
                    LOGGER.debug("AzureBillingResourceUsage " + queryErrorCode + ":" + queryErrorMessage +
                                 "(renewing token)");
                    azureBillingCredentials.renewOrOnlyGetAccessToken(true);
                    continue;
                } else if (queryErrorCode.equals("ProcessingNotCompleted") ||
                           (queryErrorCode.equals("InvalidInput") &&
                            queryErrorMessage.equals("reportedendtime cannot be in the future."))) {
                    endDateTime = endDateTime.minusHours(1);
                    LOGGER.debug("AzureBillingResourceUsage " + queryErrorCode + ":" + queryErrorMessage +
                                 "(new endDateTime=" + endDateTime + ")");
                    continue;
                } else if (queryErrorCode.equals("InvalidInput") &&
                           queryErrorMessage.equals("reportedstarttime cannot be in the future.")) {
                    LOGGER.debug("AzureBillingResourceUsage " + queryErrorCode + ":" + queryErrorMessage +
                                 "(let's try next time with the same reportedstarttime)");
                    return null;
                }
            }

            // HISTORY WILL NEVER BE RETRIEVED, throw an Exception to stop the periodical getter threads !!
            LOGGER.error("AzureBillingResourceUsage getLastResourceUsageHistory AzureBillingException " +
                         lastResourceUsageHistory);
            throw new AzureBillingException(lastResourceUsageHistory);
        }
        // DID NOT FIND AN ENDDATETIME TO GET THE HISTORY.. WILL TRY AT THE NEW PERIODICAL CALL !
        LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory cannot find an history for this period since " +
                     formatter.format(startDateTime) + " >= " + formatter.format(endDateTime));
        return null;
    }

    private double computeResourceCostInThatHour(double resourceQuantityInThatHour,
            LinkedHashMap<String, Double> meterRates) {

        LOGGER.debug("AzureBillingResourceUsage computeResourceCostInThatHour resourceQuantityInThatHour " +
                     resourceQuantityInThatHour + " meterRates " + meterRates);

        if (meterRates == null || meterRates.isEmpty()) {
            return 0;
        }

        double resourceCostInThatHour = 0;
        double quantityToPriceInThisStep;
        double lowerStepQuantity = -1;
        double lowerStepRate = -1;
        double upperStepQuantity;
        double upperStepRate;

        for (Map.Entry<String, Double> meterRatesEntry : meterRates.entrySet()) {
            upperStepQuantity = Double.parseDouble(meterRatesEntry.getKey());
            upperStepRate = meterRatesEntry.getValue();

            LOGGER.debug("AzureBillingResourceUsage computeResourceCostInThatHour step rate:[" + lowerStepRate + "," +
                         upperStepRate + "]  step quantity:[" + lowerStepQuantity + "," + upperStepQuantity + "]");

            // In [lowerStepQuantity, upperStepQuantity], it costs lowerStepRate
            if (lowerStepQuantity != -1) {

                // Which quantity to consider in this interval?
                if (resourceQuantityInThatHour >= upperStepQuantity) {
                    quantityToPriceInThisStep = upperStepQuantity - lowerStepQuantity;
                } else if (resourceQuantityInThatHour > lowerStepQuantity) {
                    quantityToPriceInThisStep = resourceQuantityInThatHour - lowerStepQuantity;
                } else {
                    break;
                }
                // Price it and add it to the global cost
                resourceCostInThatHour += quantityToPriceInThisStep * lowerStepRate;
                LOGGER.debug("AzureBillingResourceUsage computeResourceCostInThatHour added to resourceCostInThatHour: " +
                             quantityToPriceInThisStep + " x " + lowerStepRate + " [new resourceCostInThatHour = " +
                             resourceCostInThatHour + "]");
            }

            // Update lowerStepQuantity & lowerStepRate
            lowerStepQuantity = upperStepQuantity;
            lowerStepRate = upperStepRate;
        }

        // The last quantity (without upperStepQuantity)
        if (resourceQuantityInThatHour > lowerStepQuantity) {
            quantityToPriceInThisStep = resourceQuantityInThatHour - lowerStepQuantity;
            resourceCostInThatHour += quantityToPriceInThisStep * lowerStepRate;

            LOGGER.debug("AzureBillingResourceUsage computeResourceCostInThatHour last added to resourceCostInThatHour: " +
                         quantityToPriceInThisStep + " x " + lowerStepRate + " [new resourceCostInThatHour = " +
                         resourceCostInThatHour + "]");
        }

        return resourceCostInThatHour;
    }

    // synchronized to ensure we dont try to use meter ids to get the meter rates while we are updating them
    synchronized public HashSet<String> updateResourceUsageOrGetMetersIds(
            AzureBillingCredentials azureBillingCredentials, HashMap<String, LinkedHashMap<String, Double>> metersRates,
            boolean update) throws IOException, AzureBillingException {

        if (update) {

            LOGGER.debug("AzureBillingResourceUsage synchronized updateResourceUsageInfosOrGetMetersIds (update)");

            // Get the last resources usage history
            String resourceUsageHistory = getLastResourceUsageHistory(azureBillingCredentials);

            // No available resource usage history
            if (resourceUsageHistory == null)
                return null;

            // Parse resourceUsageHistory to find the desired resource usage
            Iterator<JsonElement> resourceUsageIterator = JSON_PARSER.parse(resourceUsageHistory)
                                                                     .getAsJsonObject()
                                                                     .get("value")
                                                                     .getAsJsonArray()
                                                                     .iterator();

            while (resourceUsageIterator.hasNext()) {
                JsonElement resourceUsage = resourceUsageIterator.next();

                JsonObject resourceProperties = resourceUsage.getAsJsonObject().get("properties").getAsJsonObject();

                // We need to replace '\"' in "instanceData" property to avoid exception
                String resourceInstanceData = resourceProperties.get("instanceData").getAsString().replaceAll("\\\\",
                                                                                                              "");

                String currentResourceUri = JSON_PARSER.parse(resourceInstanceData)
                                                       .getAsJsonObject()
                                                       .get("Microsoft.Resources")
                                                       .getAsJsonObject()
                                                       .get("resourceUri")
                                                       .getAsString();

                LOGGER.debug("AzureBillingResourceUsage updateResourceUsageInfosOrGetMetersIds (update) " +
                             currentResourceUri + " matches " + this.resourceUriRegex + " ? " +
                             currentResourceUri.matches(this.resourceUriRegex));

                // Here, we have a resource usage per hour (i.e.  (startDateTime) [8:00,9:00], [9:00,10:00], [10:00,11:00] (endDateTime))
                // In case of multiple VM deployed, VM names = <node source name><number>
                if (currentResourceUri.matches(this.resourceUriRegex)) {

                    LOGGER.debug("AzureBillingResourceUsage updateResourceUsageInfosOrGetMetersIds (update) considering resource " +
                                 resourceProperties);

                    double resourceQuantityInThatHour = resourceProperties.get("quantity").getAsDouble();
                    String meterId = resourceProperties.get("meterId").getAsString();

                    // Store meterId to make AzureBillingRateCard store meter rates with ids in meterIdsSet
                    this.metersIds.add(meterId);

                    // Get the meter rates of meterId
                    LinkedHashMap<String, Double> meterRates = metersRates.get(meterId);

                    if (meterRates == null) {
                        // It should never happens but in that case do not consider this resource consumption in that period for the global cost
                        LOGGER.debug("AzureBillingResourceUsage updateResourceUsageInfosOrGetMetersIds (update) cannot retrieve meter rate for " +
                                     meterId + ". The global usage cost will not include the resource " +
                                     currentResourceUri + " at this period.");
                        continue;
                    } else {
                        LOGGER.debug("AzureBillingResourceUsage updateResourceUsageInfosOrGetMetersIds (update) retrieved meter rate for meterId " +
                                     meterId + " meterRates " + meterRates);
                    }

                    double resourceCostInThatHour = computeResourceCostInThatHour(resourceQuantityInThatHour,
                                                                                  meterRates);
                    this.globalCost += resourceCostInThatHour;
                    this.budgetPercentage = this.globalCost * 100 / this.budget;

                    LOGGER.debug("AzureBillingResourceUsage updateResourceUsageInfosOrGetMetersIds (update) (in while) currentResourceUri " +
                                 currentResourceUri + " resourceCostInThatHour " + resourceCostInThatHour +
                                 " (now this.globalCost=" + this.globalCost + ") for [" +
                                 resourceProperties.get("usageStartTime").getAsString() + ";" +
                                 resourceProperties.get("usageEndTime").getAsString() + "]");

                } // END OF if (currentResourceUri.matches(this.resourceUriRegex))
            } // END OF while (resourceUsageIterator.hasNext())

            LOGGER.debug("AzureBillingResourceUsage synchronized updateResourceUsageInfosOrGetMetersIds (update) before return");
            return new HashSet<>();

        } else {
            LOGGER.debug("AzureBillingResourceUsage synchronized updateResourceUsageInfosOrGetMetersIds (get)");
            HashSet<String> metersIdsCopy = new HashSet<>(this.metersIds);
            LOGGER.debug("AzureBillingResourceUsage synchronized updateResourceUsageInfosOrGetMetersIds (get) before return");
            return metersIdsCopy;
        }
    }
}
