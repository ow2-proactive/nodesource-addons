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
package org.ow2.proactive.resourcemanager.nodesource.infrastructure.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.log4j.Logger;


public class LinuxInitScriptGenerator {

    private static final Logger logger = Logger.getLogger(LinuxInitScriptGenerator.class);

    private List<String> commands = new ArrayList<>();

    private static Configuration nsConfig = null;

    public List<String> buildScript(String instanceId, String rmUrlToUse, String rmHostname,
            String instanceTagNodeProperty, String additionalProperties, String nsName, String nodeName,
            int numberOfNodesPerInstance) {
        loadNSConfig();
        return buildScript(instanceId,
                           rmUrlToUse,
                           rmHostname,
                           rmHostname + nsConfig.getString(NSProperties.DEFAULT_SUFFIX_RM_TO_NODEJAR_URL),
                           instanceTagNodeProperty,
                           additionalProperties,
                           nsName,
                           nodeName,
                           numberOfNodesPerInstance);
    }

    public List<String> buildScript(String instanceId, String rmUrlToUse, String rmHostname, String nodeJarUrl,
            String instanceTagNodeProperty, String additionalProperties, String nsName, String nodeName,
            int numberOfNodesPerInstance) {

        loadNSConfig();
        commands.clear();

        if (nsConfig.getBoolean(NSProperties.JRE_INSTALL)) {
            commands.add(nsConfig.getString(NSProperties.JRE_INSTALL_COMMAND));
        }

        commands.add(generateNodeDownloadCommand(nodeJarUrl));

        commands.add(generateNodeStartCommand(instanceId,
                                              rmUrlToUse,
                                              rmHostname,
                                              instanceTagNodeProperty,
                                              additionalProperties,
                                              nsName,
                                              nodeName,
                                              numberOfNodesPerInstance));

        logger.info("Node starting script generated for Linux system: " + commands.toString());

        return commands;
    }

    public String generateNodeDownloadCommand(String nodeJarUrl) {
        return "wget -nv " + nodeJarUrl;
    }

    private String generateNodeStartCommand(String instanceId, String rmUrlToUse, String rmHostname,
            String instanceTagNodeProperty, String additionalProperties, String nsName, String nodeBaseName,
            int numberOfNodesPerInstance) {

        String javaCommand = nsConfig.getString(NSProperties.JAVA_COMMAND) + " -jar node.jar";

        String protocol = rmUrlToUse.substring(0, rmUrlToUse.indexOf(':')).trim();

        String nodeNamingOption = (nodeBaseName == null || nodeBaseName.isEmpty()) ? "" : " -n " + nodeBaseName;

        String javaProperties = " -Dproactive.communication.protocol=" + protocol +
                                " -Dproactive.pamr.router.address=" + rmHostname + " -D" + instanceTagNodeProperty +
                                "=" + instanceId + " " + additionalProperties + " -r " + rmUrlToUse + " -s " + nsName +
                                nodeNamingOption + " -w " + numberOfNodesPerInstance;

        return "nohup " + javaCommand + javaProperties + "  &";
    }

    public String generateDefaultIaasConnectorURL(String DefaultRMHostname) {
        if (nsConfig == null) {
            try {
                // If the configuration manager is not loaded, I load it with the NodeSource properties file
                nsConfig = NSProperties.loadConfig();
            } catch (ConfigurationException e) {
                // If something go wring, I switch to hardcoded configuration, and leave.
                logger.error("Exception when loading NodeSource properties", e);
                // return null
            }
        }
        // I return the requested value while taking into account the configuration parameters
        return nsConfig.getString(NSProperties.DEFAULT_PREFIX_CONNECTOR_IAAS_URL) + DefaultRMHostname +
               nsConfig.getString(NSProperties.DEFAULT_SUFFIX_CONNECTOR_IAAS_URL);
    }

    public String generateDefaultDownloadCommand(String rmHostname) {
        if (nsConfig == null) {
            // If the configuration manager is not loaded, I load it with the NodeSource properties file
            try {
                nsConfig = NSProperties.loadConfig();
            } catch (ConfigurationException e) {
                // If something go wring, I switch to hardcoded configuration.
                logger.error("Exception when loading NodeSource properties", e);
                // return null obviously
            }
        }
        return generateNodeDownloadCommand(rmHostname +
                                           nsConfig.getString(NSProperties.DEFAULT_SUFFIX_RM_TO_NODEJAR_URL));
    }

    private static void loadNSConfig() {
        try {
            if (null == nsConfig) {
                nsConfig = NSProperties.loadConfig();
            }
        } catch (ConfigurationException e) {
            logger.error("Exception when loading NodeSource properties", e);
        }
    }
}
