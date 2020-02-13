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
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils;


public class AzureBillingResourceUsage {

    private static final Logger LOGGER = Logger.getLogger(AzureBillingResourceUsage.class);

    private static final JsonParser JSON_PARSER = new JsonParser();

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                                                                        .withZone(ZoneOffset.UTC);

    private String subscriptionId;

    private LocalDateTime resourceUsageReportedStartDateTime = null;

    private LocalDateTime resourceUsageReportedEndDateTime = null;

    private double vmGlobalCost = 0;

    private String currency;

    private String resourceUri;

    public AzureBillingResourceUsage(String subscriptionId, String resourceGroup, String nodeSourceName,
            String currency) {

        this.subscriptionId = subscriptionId;
        this.currency = currency;

        // Since ResourceUtils.constructResourceId returns 'resourcegroups' against 'resourcesGroups' in the query result
        // we replace "resourcegroups" by "resourceGroups"
        this.resourceUri = ResourceUtils.constructResourceId(subscriptionId,
                                                             resourceGroup,
                                                             "Microsoft.Compute",
                                                             "virtualMachines",
                                                             nodeSourceName,
                                                             "")
                                        .replaceFirst("resourcegroups", "resourceGroups");

        LOGGER.debug("AzureBillingResourceUsage AzureBillingResourceUsage " + this.resourceUri);
    }

    public void setResourceUsageReportedStartDateTime(LocalDateTime resourceUsageReportedStartDateTime) {
        this.resourceUsageReportedStartDateTime = resourceUsageReportedStartDateTime;
    }

    public void setResourceUsageReportedEndDateTime(LocalDateTime resourceUsageReportedEndDateTime) {
        this.resourceUsageReportedEndDateTime = resourceUsageReportedEndDateTime;
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

        // Init start date time and end date time
        // 1. The resource usage start time (the watch time)  will probably not fit
        // with the "reported date time" (local API server time) since Azure has 19 Data Centers around the world.
        // To be sure to catch the first resource event, retrieve the resource usage history from yesterday
        // 2. With hourly granularity Azure only accept start and end date time with '00' set to minutes and seconds (i.e. truncated)
        // 3. Azure does not accept too recent end date time. Consequently we set end date time to now minus 1 hour.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nowTruncatedLastHour = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endDateTime = nowTruncatedLastHour.minusHours(1);
        LocalDateTime startDateTime;
        if (this.resourceUsageReportedEndDateTime == null) {
            // resourceUsageReportedStartDateTime is the start date of the period over which we estimate the vm usage cost.
            // It is set only once.
            this.resourceUsageReportedStartDateTime = nowTruncatedLastHour.minusDays(1);
            startDateTime = this.resourceUsageReportedStartDateTime;
        } else { // Otherwise consider the period starting right after the previous one
            startDateTime = this.resourceUsageReportedEndDateTime;
        }

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

                String queryErrorCodeMessage = jsonObject.get("error").getAsJsonObject().get("code").getAsString();

                if (queryErrorCodeMessage.equals("ExpiredAuthenticationToken")) {
                    LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory ExpiredAuthenticationToken");
                    azureBillingCredentials.renewOrOnlyGetAccessToken(true);
                    continue;
                } else if (queryErrorCodeMessage.equals("ProcessingNotCompleted")) {
                    endDateTime = endDateTime.minusHours(1);
                    LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory ProcessingNotCompleted new endDateTime " +
                                 endDateTime);
                    continue;
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

    public boolean updateVmUsageInfos(AzureBillingCredentials azureBillingCredentials,
            AzureBillingRateCard azureBillingRateCard) throws IOException, AzureBillingException {

        // Get the last resources usage history
        String resourceUsageHistory = getLastResourceUsageHistory(azureBillingCredentials);

        // No available resource usage history
        if (resourceUsageHistory == null)
            return false;

        // Parse resourceUsageHistory to find the desired resource usage
        Iterator<JsonElement> resourceUsageIterator = JSON_PARSER.parse(resourceUsageHistory)
                                                                 .getAsJsonObject()
                                                                 .get("value")
                                                                 .getAsJsonArray()
                                                                 .iterator();

        while (resourceUsageIterator.hasNext()) {
            JsonElement resourceUsage = resourceUsageIterator.next();
            JsonObject resourceProperties = resourceUsage.getAsJsonObject().get("properties").getAsJsonObject();

            // Only consider "Virtual Machines" type (do not consider IP, Disk,..)
            if (!resourceProperties.get("meterCategory").getAsString().equalsIgnoreCase("Virtual Machines"))
                continue;

            // We need to replace '\"' in "instanceData" property to avoid exception
            String resourceInstanceData = resourceProperties.get("instanceData").getAsString().replaceAll("\\\\", "");

            String currentResourceUri = JSON_PARSER.parse(new String(resourceInstanceData))
                                                   .getAsJsonObject()
                                                   .get("Microsoft.Resources")
                                                   .getAsJsonObject()
                                                   .get("resourceUri")
                                                   .getAsString();

            // Here, we have a resource usage per hour (i.e.  (startDateTime) [8:00,9:00], [9:00,10:00], [10:00,11:00] (endDateTime))
            // In case of multiple VM deployed, VM names = <node source name><number>
            if (currentResourceUri.matches(this.resourceUri + "[0-9]*$")) {

                LOGGER.debug("AzureBillingResourceUsage updateVmUsageInfos currentResourceUri " + currentResourceUri);

                double resourceQuantityInThatHour = resourceProperties.get("quantity").getAsDouble();
                String meterId = resourceProperties.get("meterId").getAsString();
                Double meterRate = azureBillingRateCard.getMeterRate(meterId);

                if (meterRate == null) {
                    // It should never happens but in that case do not consider this resource consumption in that period for the vm global cost
                    LOGGER.debug("AzureBillingResourceUsage updateVmUsageInfos cannot retrieve meter rate for " +
                                 meterId + ". The global Vm usage cost will not include the resource " +
                                 currentResourceUri + " at this period.");
                    continue;
                }

                this.vmGlobalCost += resourceQuantityInThatHour * meterRate;

                LOGGER.debug("AzureBillingResourceUsage updateVmUsageInfos (in while) for currentResourceUri " +
                             currentResourceUri + " => " + resourceQuantityInThatHour + " x " + meterRate + " = " +
                             (resourceQuantityInThatHour * meterRate) + " (now this.vmUsageCost=" + this.vmGlobalCost +
                             ") for [" + resourceProperties.get("usageStartTime").getAsString() + ";" +
                             resourceProperties.get("usageEndTime").getAsString() + "]");
            }
        }
        return true;
    }

    public LocalDateTime getResourceUsageReportedStartDateTime() {
        return this.resourceUsageReportedStartDateTime;
    }

    public LocalDateTime getResourceUsageReportedEndDateTime() {
        return this.resourceUsageReportedEndDateTime;
    }

    public double getVmGlobalCost() {
        return this.vmGlobalCost;
    }

    public void setVmGlobalCost(double vmGlobalCost) {
        this.vmGlobalCost = vmGlobalCost;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
