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

import static org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties.RM_CLOUD_INFRASTRUCTURES_DESTROY_INSTANCES_ON_SHUTDOWN;
import static org.ow2.proactive.resourcemanager.nodesource.infrastructure.AdditionalInformationKeys.*;

import java.io.IOException;
import java.security.KeyException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.node.Node;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.billing.AzureBillingCredentials;
import org.ow2.proactive.resourcemanager.nodesource.billing.AzureBillingException;
import org.ow2.proactive.resourcemanager.nodesource.billing.AzureBillingRateCard;
import org.ow2.proactive.resourcemanager.nodesource.billing.AzureBillingResourceUsage;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.util.InitScriptGenerator;

import lombok.Getter;


public class AzureInfrastructure extends AbstractAddonInfrastructure {

    private static final Logger LOGGER = Logger.getLogger(AzureInfrastructure.class);

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                                                                        .withZone(ZoneOffset.UTC);

    private static final String ADDITIONAL_INFORMATION_NOT_AVAILABLE_YET = "not available yet";

    public static final String WINDOWS = "windows";

    public static final String LINUX = "linux";

    @Getter
    private final String instanceIdNodeProperty = "instanceId";

    public static final String INFRASTRUCTURE_TYPE = "azure";

    private static final String DEFAULT_ADDITIONAL_PROPERTIES = "-Dproactive.useIPaddress=true -Dproactive.pnp.port=64738";

    private ScheduledExecutorService periodicallyResourceUsageGetter = null;

    private ScheduledExecutorService periodicallyRateCardGetter = null;

    private AzureBillingResourceUsage azureBillingResourceUsage = null;

    private AzureBillingRateCard azureBillingRateCard = null;

    private AzureBillingCredentials azureBillingCredentials = null;

    private boolean initBillingIsDone = false;

    private final transient InitScriptGenerator initScriptGenerator = new InitScriptGenerator();

    // The index of the infrastructure configurable parameters.
    private enum Indexes {
        CLIENT_ID(0),
        SECRET(1),
        DOMAIN(2),
        SUBSCRIPTION_ID(3),
        AUTHENTICATION_ENDPOINT(4),
        MANAGEMENT_ENDPOINT(5),
        RESOURCE_MANAGER_ENDPOINT(6),
        GRAPH_ENDPOINT(7),
        RM_HOSTNAME(8),
        CONNECTOR_IAAS_URL(9),
        IMAGE(10),
        IMAGE_OS_TYPE(11),
        VM_SIZE_TYPE(12),
        VM_USERNAME(13),
        VM_PASSWORD(14),
        VM_PUBLIC_KEY(15),
        RESOURCE_GROUP(16),
        REGION(17),
        NUMBER_OF_INSTANCES(18),
        NUMBER_OF_NODES_PER_INSTANCE(19),
        NODE_JAR_URL(20),
        PRIVATE_NETWORK_CIDR(21),
        STATIC_PUBLIC_IP(22),
        ADDITIONAL_PROPERTIES(23),
        BILLING_RESOURCE_USAGE_REFRESH_FREQ_IN_MIN(24),
        BILLING_RATE_CARD_REFRESH_FREQ_IN_MIN(25),
        BILLING_OFFER_ID(26),
        BILLING_CURRENCY(27),
        BILLING_LOCALE(28),
        BILLING_REGION_INFO(29),
        BILLING_BUDGET(30),
        LINUX_STARTUP_SCRIPT(31),
        WINDOWS_STARTUP_SCRIPT(32);

        protected int index;

        Indexes(int index) {
            this.index = index;
        }
    }

    @Configurable(description = "The Azure clientId", sectionSelector = 1, important = true)
    protected String clientId = null;

    @Configurable(description = "The Azure secret key", sectionSelector = 1, important = true)
    protected String secret = null;

    @Configurable(description = "The Azure domain or tenantId", sectionSelector = 1, important = true)
    protected String domain = null;

    @Configurable(description = "The Azure subscriptionId to use (if not specified, it will try to use the default one)", sectionSelector = 1, important = true)
    protected String subscriptionId = null;

    @Configurable(description = "Optional authentication endpoint from specific Azure environment", sectionSelector = 3)
    protected String authenticationEndpoint = null;

    @Configurable(description = "Optional management endpoint from specific Azure environment", sectionSelector = 3)
    protected String managementEndpoint = null;

    @Configurable(description = "Optional resource manager endpoint from specific Azure environment", sectionSelector = 3)
    protected String resourceManagerEndpoint = null;

    @Configurable(description = "Optional graph endpoint from specific Azure environment", sectionSelector = 3)
    protected String graphEndpoint = null;

    @Configurable(description = "Resource manager hostname or ip address (must be accessible from nodes)", sectionSelector = 4)
    protected String rmHostname = generateDefaultRMHostname();

    @Configurable(description = "Connector-iaas URL", sectionSelector = 4)
    protected String connectorIaasURL = InitScriptGenerator.generateDefaultIaasConnectorURL(generateDefaultRMHostname());

    @Configurable(description = "Image (name or key)", sectionSelector = 6, important = true)
    protected String image = null;

    @Configurable(description = "Image OS type (choose between 'linux' and 'windows', default: 'linux')", sectionSelector = 6, important = true)
    protected String imageOSType = LINUX;

    @Configurable(description = "Azure virtual machine size type (by default: 'Standard_D1_v2')", sectionSelector = 6, important = true)
    protected String vmSizeType = null;

    @Configurable(description = "The virtual machine Username", sectionSelector = 6, important = true)
    protected String vmUsername = null;

    @Configurable(description = "The virtual machine Password", password = true, sectionSelector = 6, important = true)
    protected String vmPassword = null;

    @Configurable(description = "A public key to allow SSH connection to the VM", sectionSelector = 6)
    protected String vmPublicKey = null;

    @Configurable(description = "The Azure resourceGroup to use (if not specified, the one from the image will be used)", sectionSelector = 6, important = true)
    protected String resourceGroup = null;

    @Configurable(description = "The Azure Region to use (if not specified, the one from the image will be used)", sectionSelector = 6)
    protected String region = null;

    @Configurable(description = "Total instance to create", sectionSelector = 5, important = true)
    protected int numberOfInstances = 1;

    @Configurable(description = "Total nodes to create per instance", sectionSelector = 5, important = true)
    protected int numberOfNodesPerInstance = 1;

    @Configurable(description = "URL used to download the node jar on the VM", sectionSelector = 8)
    protected String nodeJarURL = InitScriptGenerator.generateDefaultNodeJarURL(generateDefaultRMHostname());

    @Configurable(description = "Optional network CIDR to attach with new VM(s) (by default: '10.0.0.0/24')", sectionSelector = 7)
    protected String privateNetworkCIDR = null;

    @Configurable(description = "Optional flag to specify if the public IP(s) of the new VM(s) must be static ('true' by default)", checkbox = true, sectionSelector = 7)
    protected boolean staticPublicIP = true;

    @Configurable(description = "Additional Java command properties (e.g. \"-Dpropertyname=propertyvalue\")", sectionSelector = 8)
    protected String additionalProperties = DEFAULT_ADDITIONAL_PROPERTIES;

    @Configurable(description = "Periodical resource usage retrieving delay in min.", sectionSelector = 2)
    protected int resourceUsageRefreshFreqInMin = 30;

    @Configurable(description = "Periodical rate card retrieving delay in min.", sectionSelector = 2)
    protected int rateCardRefreshFreqInMin = 30;

    @Configurable(description = "The Offer ID parameter consists of the 'MS-AZR-' prefix, plus the Offer ID number.", sectionSelector = 2)
    protected String offerId = "MS-AZR-0003p";

    @Configurable(description = "The currency in which the resource rates need to be provided.", sectionSelector = 2)
    protected String currency = "USD";

    @Configurable(description = "The culture in which the resource metadata needs to be localized.", sectionSelector = 2)
    protected String locale = "en-US";

    @Configurable(description = "The 2 letter ISO code where the offer was purchased.", sectionSelector = 2)
    protected String regionInfo = "US";

    @Configurable(description = "Your budget for this node source related Azure resources. Will be used to compute your global cost in % budget.", sectionSelector = 2)
    protected double maxBudget = 50;

    @Configurable(textArea = true, description = "Linux VM startup script to launch the ProActive nodes (optional). Please refer to the documentation for full description. (optional)", sectionSelector = 8)
    protected String linuxStartupScript = initScriptGenerator.getDefaultLinuxStartupScript();

    @Configurable(textArea = true, description = "Powershell script to be run during Windows VM startup for launching the ProActive nodes (optional). Please refer to the documentation for full description. (optional)", sectionSelector = 8)
    protected String windowsStartupScript = initScriptGenerator.getDefaultWindowsStartupScript();

    @Override
    public void configure(Object... parameters) {

        LOGGER.info("Validating parameters");
        if (parameters == null || parameters.length < Indexes.values().length) {
            throw new IllegalArgumentException("Invalid parameters for AzureInfrastructure creation");
        }

        this.clientId = parseMandatoryParameter("clientId", parameters[Indexes.CLIENT_ID.index]);
        this.secret = parseMandatoryParameter("secret", parameters[Indexes.SECRET.index]);
        this.domain = parseMandatoryParameter("domain", parameters[Indexes.DOMAIN.index]);
        this.subscriptionId = parseOptionalParameter(parameters[Indexes.SUBSCRIPTION_ID.index]);
        this.authenticationEndpoint = parseOptionalParameter(parameters[Indexes.AUTHENTICATION_ENDPOINT.index]);
        this.managementEndpoint = parseOptionalParameter(parameters[Indexes.MANAGEMENT_ENDPOINT.index]);
        this.resourceManagerEndpoint = parseOptionalParameter(parameters[Indexes.RESOURCE_MANAGER_ENDPOINT.index]);
        this.graphEndpoint = parseOptionalParameter(parameters[Indexes.GRAPH_ENDPOINT.index]);
        this.rmHostname = parseMandatoryParameter("rmHostname", parameters[Indexes.RM_HOSTNAME.index]);
        this.connectorIaasURL = parseMandatoryParameter("connectorIaasURL",
                                                        parameters[Indexes.CONNECTOR_IAAS_URL.index]);
        this.image = parseMandatoryParameter("image", parameters[Indexes.IMAGE.index]);
        this.imageOSType = parseMandatoryParameter("imageOSType",
                                                   parameters[Indexes.IMAGE_OS_TYPE.index]).toLowerCase();
        if (!imageOSType.equalsIgnoreCase(WINDOWS) && !imageOSType.equalsIgnoreCase(LINUX)) {
            throw new IllegalArgumentException("The image OS type is not recognized, it must be 'windows' or 'linux'");
        }
        this.vmSizeType = parseOptionalParameter(parameters[Indexes.VM_SIZE_TYPE.index]);
        this.vmUsername = parseMandatoryParameter("vmUsername", parameters[Indexes.VM_USERNAME.index]);
        this.vmPassword = parseMandatoryParameter("vmPassword", parameters[Indexes.VM_PASSWORD.index]);
        this.vmPublicKey = parseOptionalParameter(parameters[Indexes.VM_PUBLIC_KEY.index]);
        this.resourceGroup = parseOptionalParameter(parameters[Indexes.RESOURCE_GROUP.index]);
        this.region = parseOptionalParameter(parameters[Indexes.REGION.index]);
        this.numberOfInstances = parseIntParameter("numberOfInstances", parameters[Indexes.NUMBER_OF_INSTANCES.index]);
        this.numberOfNodesPerInstance = parseIntParameter("numberOfNodesPerInstance",
                                                          parameters[Indexes.NUMBER_OF_NODES_PER_INSTANCE.index]);
        this.nodeJarURL = parseMandatoryParameter("nodeJarURL", parameters[Indexes.NODE_JAR_URL.index]);
        this.privateNetworkCIDR = parseOptionalParameter(parameters[Indexes.PRIVATE_NETWORK_CIDR.index]);
        this.staticPublicIP = parseBooleanParameter("staticPublicIP", parameters[Indexes.STATIC_PUBLIC_IP.index]);
        this.additionalProperties = parseOptionalParameter(parameters[Indexes.ADDITIONAL_PROPERTIES.index]);
        this.resourceUsageRefreshFreqInMin = parseIntParameter("resourceUsageRefreshFreqInMin",
                                                               parameters[Indexes.BILLING_RESOURCE_USAGE_REFRESH_FREQ_IN_MIN.index]);
        this.rateCardRefreshFreqInMin = parseIntParameter("rateCardRefreshFreqInMin",
                                                          parameters[Indexes.BILLING_RATE_CARD_REFRESH_FREQ_IN_MIN.index]);
        this.offerId = parseOptionalParameter(parameters[Indexes.BILLING_OFFER_ID.index]);
        this.currency = parseOptionalParameter(parameters[Indexes.BILLING_CURRENCY.index]);
        this.locale = parseOptionalParameter(parameters[Indexes.BILLING_LOCALE.index]);
        this.regionInfo = parseOptionalParameter(parameters[Indexes.BILLING_REGION_INFO.index]);
        this.maxBudget = parseDoubleParameter("maxBudget", parameters[Indexes.BILLING_BUDGET.index]);
        this.linuxStartupScript = parseOptionalParameter(parameters[Indexes.LINUX_STARTUP_SCRIPT.index],
                                                         initScriptGenerator.getDefaultLinuxStartupScript());
        this.windowsStartupScript = parseOptionalParameter(parameters[Indexes.WINDOWS_STARTUP_SCRIPT.index],
                                                           initScriptGenerator.getDefaultWindowsStartupScript());
        this.connectorIaasController = new ConnectorIaasController(connectorIaasURL, INFRASTRUCTURE_TYPE);
    }

    private void restoreBillingInformation() {

        HashMap<String, String> additionalInformation = this.nodeSource.getAdditionalInformation();

        LOGGER.info("AzureInfrastructure restoreBillingInformation additionalInformation " +
                    Arrays.asList(additionalInformation));

        if (additionalInformation != null && additionalInformation.get(CLOUD_COST_GLOBAL_COST_KEY) != null) {
            this.azureBillingResourceUsage.setResourceUsageReportedStartDateTime(LocalDateTime.parse(additionalInformation.get(CLOUD_COST_RESOURCE_USAGE_REPORTED_AT_KEY),
                                                                                                     formatter));
            this.azureBillingResourceUsage.setResourceUsageReportedEndDateTime(LocalDateTime.parse(additionalInformation.get(CLOUD_COST_RESOURCE_USAGE_REPORTED_UNTIL_KEY),
                                                                                                   formatter));
            this.azureBillingResourceUsage.setCurrency(additionalInformation.get(CLOUD_COST_CURRENCY_KEY));
            this.azureBillingResourceUsage.setBudget(Double.parseDouble(additionalInformation.get(CLOUD_COST_MAX_BUDGET_KEY)));

            if (additionalInformation.get(CLOUD_COST_GLOBAL_COST_KEY)
                                     .equals(ADDITIONAL_INFORMATION_NOT_AVAILABLE_YET)) {
                this.azureBillingResourceUsage.setGlobalCost(0);
                this.azureBillingResourceUsage.setBudgetPercentage(0);
            } else {
                this.azureBillingResourceUsage.setGlobalCost(Double.parseDouble(additionalInformation.get(CLOUD_COST_GLOBAL_COST_KEY)));
                this.azureBillingResourceUsage.setBudgetPercentage(Double.parseDouble(additionalInformation.get(CLOUD_COST_GLOBAL_COST_IN_MAX_BUDGET_PERCENTAGE_KEY)));
            }
        }
    }

    private void initBilling() {

        if (!this.initBillingIsDone) {

            // Azure billing instances
            try {
                LOGGER.info("AzureInfrastructure initBilling new AzureBillingCredentials " + this.clientId + " " +
                            this.domain + " " + this.secret);
                this.azureBillingCredentials = new AzureBillingCredentials(this.clientId, this.domain, this.secret);
            } catch (IOException e) {
                LOGGER.error("AzureInfrastructure initBilling " + e.getMessage() + "? Will not start getter threads.");
                return;
            }
            LOGGER.info("AzureInfrastructure initBilling new AzureBillingResourceUsage " + this.subscriptionId + " " +
                        this.resourceGroup + " " + this.nodeSource.getName() + " " + this.currency + " " +
                        this.maxBudget);
            this.azureBillingResourceUsage = new AzureBillingResourceUsage(this.subscriptionId,
                                                                           this.resourceGroup,
                                                                           this.nodeSource.getName(),
                                                                           this.currency,
                                                                           this.maxBudget);

            LOGGER.info("AzureInfrastructure initBilling new AzureBillingRateCard " + this.subscriptionId + " " +
                        this.offerId + " " + this.currency + " " + this.locale + " " + this.regionInfo);
            this.azureBillingRateCard = new AzureBillingRateCard(this.subscriptionId,
                                                                 this.offerId,
                                                                 this.currency,
                                                                 this.locale,
                                                                 this.regionInfo);

            // Restore infos if possible
            restoreBillingInformation();

            // Start a new thread to periodically call getAndStoreRate
            // In case of already having a running tread, i.e. which was not shut down, no need create a new thread
            if (this.periodicallyRateCardGetter == null) {
                this.periodicallyRateCardGetter = Executors.newSingleThreadScheduledExecutor();
            }
            this.periodicallyRateCardGetter.scheduleAtFixedRate(this::updateMetersRates,
                                                                0,
                                                                this.rateCardRefreshFreqInMin,
                                                                TimeUnit.MINUTES);

            // Start a new thread to periodically retrieve resource usage
            // Start it after periodicallyRateCardGetter (cf initial delay param) since rates are required
            // to compute the global cost
            if (this.periodicallyResourceUsageGetter == null) {
                this.periodicallyResourceUsageGetter = Executors.newSingleThreadScheduledExecutor();
            }
            this.periodicallyResourceUsageGetter.scheduleAtFixedRate(this::updateResourceUsage,
                                                                     2,
                                                                     this.resourceUsageRefreshFreqInMin,
                                                                     TimeUnit.MINUTES);

            LOGGER.info("AzureInfrastructure initBilling periodicallyResourceUsageGetter and periodicallyRateCardGetter started");
            this.initBillingIsDone = true;
        }
    }

    synchronized private void shutdownBillingGetters() {
        LOGGER.info("AzureInfrastructure shutdownBillingGetters");
        // Shutdown threads
        if (this.periodicallyResourceUsageGetter != null) {
            this.periodicallyResourceUsageGetter.shutdownNow();
        }
        if (this.periodicallyRateCardGetter != null) {
            this.periodicallyRateCardGetter.shutdownNow();
        }
    }

    @Override
    public void shutDown() {
        LOGGER.info("Shutting down");
        super.shutDown();
        shutdownBillingGetters();
    }

    @Override
    public void acquireNode() {

        // Init billing objects
        initBilling();

        connectorIaasController.waitForConnectorIaasToBeUP();

        // this is going to terminate the infrastructure and to restart it,
        // by reinitializing the rest paths. Even in case of RM recovery, it
        // is safer to proceed this way since we don't know if the connector
        // IAAS was standalone or running as part of the scheduler. We must
        // make sure that the REST paths are set up.
        connectorIaasController.createAzureInfrastructure(getInfrastructureId(),
                                                          clientId,
                                                          secret,
                                                          domain,
                                                          subscriptionId,
                                                          authenticationEndpoint,
                                                          managementEndpoint,
                                                          resourceManagerEndpoint,
                                                          graphEndpoint,
                                                          RM_CLOUD_INFRASTRUCTURES_DESTROY_INSTANCES_ON_SHUTDOWN.getValueAsBoolean());

        String instanceTag = getInfrastructureId();
        Set<String> instancesIds;
        boolean existPersistedInstanceIds = false;

        if (expectInstancesAlreadyCreated(false, true)) {
            // it is a fresh deployment: create or retrieve all instances
            instancesIds = connectorIaasController.createAzureInstances(getInfrastructureId(),
                                                                        instanceTag,
                                                                        image,
                                                                        numberOfInstances,
                                                                        vmUsername,
                                                                        vmPassword,
                                                                        vmPublicKey,
                                                                        vmSizeType,
                                                                        resourceGroup,
                                                                        region,
                                                                        privateNetworkCIDR,
                                                                        staticPublicIP);
            LOGGER.info("Instances ids created or retrieved : " + instancesIds);
        } else {
            // if the infrastructure was already created, then wee need to
            // look at the free instances, if any (the ones on which no node
            // run. In the current implementation, this can only happen when
            // nodes are down. Indeed if they are all removed on purpose, the
            // instance should be shut down). Note that in this case, if the
            // free instances map is empty, no script will be run at all.
            Map<String, Integer> freeInstancesMap = getInstancesWithoutNodesMapCopy();
            instancesIds = freeInstancesMap.keySet();
            LOGGER.info("Instances ids previously saved which require script re-execution: " + instancesIds);
            existPersistedInstanceIds = true;
        }

        // execute script on instances to deploy or redeploy nodes on them
        for (String currentInstanceId : instancesIds) {
            try {
                List<String> scripts = imageOSType.equalsIgnoreCase(WINDOWS) ? initScriptGenerator.buildWindowsScript(windowsStartupScript,
                                                                                                                      currentInstanceId,
                                                                                                                      getRmUrl(),
                                                                                                                      rmHostname,
                                                                                                                      nodeJarURL,
                                                                                                                      instanceIdNodeProperty,
                                                                                                                      additionalProperties,
                                                                                                                      nodeSource.getName(),
                                                                                                                      currentInstanceId,
                                                                                                                      numberOfNodesPerInstance,
                                                                                                                      getCredentials())
                                                                             : initScriptGenerator.buildLinuxScript(linuxStartupScript,
                                                                                                                    currentInstanceId,
                                                                                                                    getRmUrl(),
                                                                                                                    rmHostname,
                                                                                                                    nodeJarURL,
                                                                                                                    instanceIdNodeProperty,
                                                                                                                    additionalProperties,
                                                                                                                    nodeSource.getName(),
                                                                                                                    currentInstanceId,
                                                                                                                    numberOfNodesPerInstance,
                                                                                                                    getCredentials());

                connectorIaasController.executeScript(getInfrastructureId(), currentInstanceId, scripts);
            } catch (KeyException e) {
                LOGGER.error("A problem occurred while acquiring user credentials path. The node startup script will be not executed.");
            } catch (ScriptNotExecutedException exception) {
                boolean acquireNodeTriggered = handleScriptNotExecutedException(existPersistedInstanceIds,
                                                                                currentInstanceId,
                                                                                exception);
                if (acquireNodeTriggered) {
                    // in this case we re-attempted a deployment, so we need
                    // to stop looping
                    break;
                }
            } finally {
                // in all cases, we must remove the instance from the free
                // instance map as we tried everything to deploy nodes on it
                removeFromInstancesWithoutNodesMap(currentInstanceId);
            }
        }

    }

    @Override
    public void acquireAllNodes() {
        acquireNode();
    }

    @Override
    public void removeNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        try {
            node.getProActiveRuntime().killNode(node.getNodeInformation().getName());

        } catch (Exception e) {
            LOGGER.warn("Unable to remove the node '" + node.getNodeInformation().getName() + "' with error: " + e);
        }

        unregisterNodeAndRemoveInstanceIfNeeded(instanceId,
                                                node.getNodeInformation().getName(),
                                                getInfrastructureId(),
                                                true);
    }

    @Override
    public void notifyAcquiredNode(Node node) throws RMException {

        String instanceId = getInstanceIdProperty(node);

        addNewNodeForInstance(instanceId, node.getNodeInformation().getName());
    }

    @Override
    public String getDescription() {
        return "Handles nodes from Microsoft Azure.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getDescription();
    }

    @Override
    public Map<Integer, String> getSectionDescriptions() {
        Map<Integer, String> sectionDescriptions = super.getSectionDescriptions();
        sectionDescriptions.put(1, "Azure Configuration");
        sectionDescriptions.put(2, "Azure Billing Configuration");
        sectionDescriptions.put(3, "Endpoints");
        sectionDescriptions.put(4, "PA Server Configuration");
        sectionDescriptions.put(5, "Deployment Configuration");
        sectionDescriptions.put(6, "VM Configuration");
        sectionDescriptions.put(7, "Network Configuration");
        sectionDescriptions.put(8, "Node Configuration");
        return sectionDescriptions;
    }

    public void updateResourceUsage() {

        LOGGER.info("AzureInfrastructure updateResourceUsage");

        try {

            HashMap<String, LinkedHashMap<String, Double>> metersRates = azureBillingRateCard.updateOrGetMetersRates(null,
                                                                                                                     null,
                                                                                                                     false);

            // Retrieve new resource usage cost infos
            if (this.azureBillingResourceUsage.updateResourceUsageOrGetMetersIds(this.azureBillingCredentials,
                                                                                 metersRates,
                                                                                 true) == null) {
                LOGGER.info("AzureInfrastructure updateResourceUsage no new resource usage infos");
                return;
            }
        } catch (IOException | AzureBillingException e) {
            LOGGER.error(e.getMessage());
            shutdownBillingGetters();
            return;
        }

        LocalDateTime resourceUsageReportedStartDateTime = this.azureBillingResourceUsage.getResourceUsageReportedStartDateTime();
        LocalDateTime resourceUsageReportedEndDateTime = this.azureBillingResourceUsage.getResourceUsageReportedEndDateTime();
        double globalCost = this.azureBillingResourceUsage.getGlobalCost();
        double budgetPercentage = this.azureBillingResourceUsage.getBudgetPercentage();

        LOGGER.info("AzureInfrastructure updateResourceUsage resourceUsageReportedStartDateTime " +
                    resourceUsageReportedStartDateTime + " resourceUsageReportedEndDateTime " +
                    resourceUsageReportedEndDateTime + " globalCost " + globalCost + " budgetPercentage " +
                    budgetPercentage);

        // Make the usage cost available as an additional information
        this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_RESOURCE_USAGE_REPORTED_AT_KEY,
                                                           formatter.format(resourceUsageReportedStartDateTime));
        this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_RESOURCE_USAGE_REPORTED_UNTIL_KEY,
                                                           formatter.format(resourceUsageReportedEndDateTime));
        this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_CURRENCY_KEY, this.currency);
        this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_MAX_BUDGET_KEY, this.maxBudget + "");

        if (globalCost == 0) {
            this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_GLOBAL_COST_KEY,
                                                               ADDITIONAL_INFORMATION_NOT_AVAILABLE_YET);
            this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_GLOBAL_COST_IN_MAX_BUDGET_PERCENTAGE_KEY,
                                                               ADDITIONAL_INFORMATION_NOT_AVAILABLE_YET);
        } else {
            this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_GLOBAL_COST_KEY, globalCost + "");
            this.nodeSource.putAndPersistAdditionalInformation(CLOUD_COST_GLOBAL_COST_IN_MAX_BUDGET_PERCENTAGE_KEY,
                                                               budgetPercentage + "");
        }
    }

    public void updateMetersRates() {

        LOGGER.info("AzureInfrastructure updateMetersRates");

        try {
            // Retrieve meterIdsSet if availables to store only needed rates
            HashSet<String> metersIdsSet = this.azureBillingResourceUsage.updateResourceUsageOrGetMetersIds(null,
                                                                                                            null,
                                                                                                            false);

            // Retrieve new rates
            this.azureBillingRateCard.updateOrGetMetersRates(this.azureBillingCredentials, metersIdsSet, true);
        } catch (IOException | AzureBillingException e) {
            LOGGER.error(e.getMessage());
            // No need to keep getter threads alive
            shutdownBillingGetters();
            return;
        }
    }
}
