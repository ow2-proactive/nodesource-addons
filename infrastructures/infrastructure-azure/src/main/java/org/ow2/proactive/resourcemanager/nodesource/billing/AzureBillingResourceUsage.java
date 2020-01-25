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

    private LocalDateTime resourceUsageReportedEndDateTime = null;

    private double vmUsageCost = 0;

    private String resourceUri = null;

    public AzureBillingResourceUsage(String subscriptionId, String resourceGroup, String nodeSourceName) {
        LOGGER.debug("AzureBillingResourceUsage contructor subscriptionId " + subscriptionId + " resourceGroup " +
                     resourceGroup + " nodeSourceName " + nodeSourceName);
        constructAndSetResourceUri(subscriptionId, resourceGroup, nodeSourceName);
    }

    private void constructAndSetResourceUri(String subscriptionId, String resourceGroup, String nodeSourceName) {

        this.resourceUri = ResourceUtils.constructResourceId(subscriptionId,
                                                             resourceGroup,
                                                             "Microsoft.Compute",
                                                             "virtualMachines",
                                                             nodeSourceName,
                                                             "");

        LOGGER.debug("AzureBillingResourceUsage constructAndSetResourceUri " + this.resourceUri);
    }

    private String queryResourceUsageHistory(String subscriptionId, String reportedStartTime, String reportedEndTime,
            String aggregationGranularity, String showDetails, String accessToken) throws IOException {

        String endpoint = String.format("https://management.azure.com/subscriptions/%s/providers/Microsoft.Commerce/UsageAggregates?api-version=%s&reportedStartTime=%s&reportedEndTime=%s&aggregationGranularity=%s&showDetails=%s",
                                        subscriptionId,
                                        "2015-06-01-preview",
                                        reportedStartTime,
                                        reportedEndTime,
                                        aggregationGranularity,
                                        showDetails)
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

    String getLastResourceUsageHistory(String subscriptionId, AzureBillingCredentials azureBillingCredentials)
            throws IOException, AzureBillingException {

        // Init start date time and end date time
        // 1. The resource usage start time (the watch time)  will probably not fit
        // with the "reported date time" (local API server time) since Azure has 19 Data Centers around the world.
        // To be sure to catch the first resource event, retrieve the resource usage history from yesterday
        // 2. With hourly granularity Azure only accept start and end date time with '00' set to minutes and seconds (i.e. truncated)
        // 3. Azure does not accept too recent end date time. Consequently we set end date time to now minus 1 hour.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nowTruncatedLastHour = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endDateTime = nowTruncatedLastHour.minusHours(1);
        String startDateTimeStr = null;
        if (this.resourceUsageReportedEndDateTime == null) {
            startDateTimeStr = formatter.format(nowTruncatedLastHour.minusDays(1));
        } else { // Otherwise consider the period starting right after the previous one
            startDateTimeStr = formatter.format(this.resourceUsageReportedEndDateTime);
        }

        // Get resources history by considering the max end date time
        // If this latter is not processing yet, try again one hour before
        // In case the end date time fall below the start date time, it will throw CannotGetResourceUsageException
        String lastResourceUsageHistory = null;
        boolean infosNotAvailableYet = true;
        while (infosNotAvailableYet) {

            lastResourceUsageHistory = queryResourceUsageHistory(subscriptionId,
                                                                 startDateTimeStr,
                                                                 formatter.format(endDateTime),
                                                                 "Hourly",
                                                                 "true",
                                                                 azureBillingCredentials.renewOrOnlyGetAccessToken(false));

            LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory considering [" + startDateTimeStr +
                         ";" + formatter.format(endDateTime) + "] = " + lastResourceUsageHistory);

            JsonObject jsonObject = new JsonParser().parse(new String(lastResourceUsageHistory)).getAsJsonObject();
            if (jsonObject.has("error")) {

                String queryErrorCodeMessage = jsonObject.get("error").getAsJsonObject().get("code").getAsString();

                if (queryErrorCodeMessage.equals("ProcessingNotCompleted")) {

                    infosNotAvailableYet = true;
                    endDateTime = endDateTime.minusHours(1);

                    LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory ProcessingNotCompleted new endDateTime " +
                                 endDateTime);

                    continue;
                } else if (queryErrorCodeMessage.equals("ExpiredAuthenticationToken")) {
                    LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory ExpiredAuthenticationToken");

                    azureBillingCredentials.renewOrOnlyGetAccessToken(true);
                    continue;
                } else {
                    LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory AzureBillingException " +
                                 queryErrorCodeMessage);

                    throw new AzureBillingException(queryErrorCodeMessage);
                }
            } else {
                infosNotAvailableYet = false;
                continue;
            }
        }

        // Update lastEndDateTimeConsideredForCost
        this.resourceUsageReportedEndDateTime = endDateTime;

        LOGGER.debug("AzureBillingResourceUsage getLastResourceUsageHistory finally considering [" + startDateTimeStr +
                     "," + formatter.format(endDateTime) + "]");

        return lastResourceUsageHistory;
    }

    public void updateVmUsageInfos(String subscriptionId, AzureBillingCredentials azureBillingCredentials,
            AzureBillingRateCard azureBillingRateCard) throws IOException, AzureBillingException {

        // Get the last resources usage history
        String resourceUsageHistory = getLastResourceUsageHistory(subscriptionId, azureBillingCredentials);

        // Parse resourceUsageHistory to find the desired resource usage
        Iterator<JsonElement> resourceUsageIterator = new JsonParser().parse(new String(resourceUsageHistory))
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

            String currentResourceUri = new JsonParser().parse(new String(resourceInstanceData))
                                                        .getAsJsonObject()
                                                        .get("Microsoft.Resources")
                                                        .getAsJsonObject()
                                                        .get("resourceUri")
                                                        .getAsString();

            LOGGER.debug("AzureBillingResourceUsage updateVmUsageInfos (in while)\ncurrentResourceUri:" +
                         currentResourceUri + "\nthis.resourceUri " + this.resourceUri + "\nequals? " +
                         currentResourceUri.equalsIgnoreCase(resourceUri));

            // We ignore case since ResourceUtils.constructResourceId returns 'resourcegroups' (against 'resourcesGroups' in the query result)
            // Here, we have a resource usage per hour (i.e.  (startDateTime) 8:00-9:00, 9:00-10:00, 10:00-11:00 (endDateTime))
            if (currentResourceUri.equalsIgnoreCase(resourceUri)) {
                double resourceQuantityInThatHour = resourceProperties.get("quantity").getAsDouble();
                String meterId = resourceProperties.get("meterId").getAsString();
                double meterRate = azureBillingRateCard.getMeterRate(meterId);
                this.vmUsageCost += resourceQuantityInThatHour * meterRate;

                LOGGER.debug("AzureBillingResourceUsage updateVmUsageInfos (in while) stored " +
                             resourceQuantityInThatHour + " x " + meterRate + " = " +
                             (resourceQuantityInThatHour * meterRate) + " (now this.vmUsageCost=" + this.vmUsageCost +
                             ") for [" + resourceProperties.get("usageStartTime").getAsString() + ";" +
                             resourceProperties.get("usageEndTime").getAsString() + "]");
            }
        }
    }

    public void updateAndgetVmUsageCostInfos(String subscriptionId, AzureBillingCredentials azureBillingCredentials,
            AzureBillingRateCard azureBillingRateCard) throws IOException, AzureBillingException {

        // Update vm usage infos
        updateVmUsageInfos(subscriptionId, azureBillingCredentials, azureBillingRateCard);
    }

    public LocalDateTime getResourceUsageReportedEndDateTime() {
        return this.resourceUsageReportedEndDateTime;
    }

    public void setResourceUsageReportedEndDateTime(LocalDateTime resourceUsageReportedEndDateTime) {
        this.resourceUsageReportedEndDateTime = resourceUsageReportedEndDateTime;
    }

    public double getVmUsageCost() {
        return this.vmUsageCost;
    }

    public void setVmUsageCost(double vmUsageCost) {
        this.vmUsageCost = vmUsageCost;
    }

}