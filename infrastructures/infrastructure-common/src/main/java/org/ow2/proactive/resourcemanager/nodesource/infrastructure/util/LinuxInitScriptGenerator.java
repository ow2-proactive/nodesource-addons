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
import org.apache.log4j.Logger;


public class LinuxInitScriptGenerator {

    private static final Logger logger = Logger.getLogger(LinuxInitScriptGenerator.class);

    private static Configuration nsConfig;

    static {
        try {
            // load configuration manager with the NodeSource properties file
            nsConfig = NSProperties.loadConfig();
        } catch (ConfigurationException e) {
            logger.error("Exception when loading NodeSource properties", e);
            throw new RuntimeException(e);
        }
    }

    public List<String> buildScript(String instanceId, String rmUrlToUse, String rmHostname,
            String instanceTagNodeProperty, String additionalProperties, String nsName, String nodeName,
            int numberOfNodesPerInstance, String credentials) {
        return buildScript(instanceId,
                           rmUrlToUse,
                           rmHostname,
                           generateDefaultNodeJarURL(rmHostname),
                           instanceTagNodeProperty,
                           additionalProperties,
                           nsName,
                           nodeName,
                           numberOfNodesPerInstance,
                           credentials);
    }

    public List<String> buildScript(String instanceId, String rmUrlToUse, String rmHostname, String nodeJarUrl,
            String instanceTagNodeProperty, String additionalProperties, String nsName, String nodeName,
            int numberOfNodesPerInstance, String credentials) {
        List<String> commands = new ArrayList<>();

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
                                              numberOfNodesPerInstance,
                                              credentials));

        logger.info("Node starting script generated for Linux system: " + commands.toString());

        return commands;
    }

    public static String generateNodeDownloadCommand(String nodeJarUrl) {
        return "wget -nv " + nodeJarUrl;
    }

    private String generateNodeStartCommand(String instanceId, String rmUrlToUse, String rmHostname,
            String instanceTagNodeProperty, String additionalProperties, String nsName, String nodeBaseName,
            int numberOfNodesPerInstance, String credentials) {

        String javaCommand = nsConfig.getString(NSProperties.JAVA_COMMAND) + " -jar node.jar";

        String protocol = rmUrlToUse.substring(0, rmUrlToUse.indexOf(':')).trim();

        String nodeNamingOption = (nodeBaseName == null || nodeBaseName.isEmpty()) ? "" : " -n " + nodeBaseName;

        String jythonPath = nsConfig.getString(NSProperties.DEFAULT_JYTHON_PATH);

        String javaProperties = " -Dproactive.communication.protocol=" + protocol + " -Dpython.path=" + jythonPath +
                                " -Dproactive.pamr.router.address=" + rmHostname + " -D" + instanceTagNodeProperty +
                                "=" + instanceId + " " + additionalProperties + " -r " + rmUrlToUse + " -s " + nsName +
                                nodeNamingOption + " -v " + credentials + " -w " + numberOfNodesPerInstance;

        return "nohup " + javaCommand + javaProperties + "  &";
    }

    public String generateDefaultIaasConnectorURL(String DefaultRMHostname) {
        // I return the requested value while taking into account the configuration parameters
        return nsConfig.getString(NSProperties.DEFAULT_PREFIX_CONNECTOR_IAAS_URL) + DefaultRMHostname +
               nsConfig.getString(NSProperties.DEFAULT_SUFFIX_CONNECTOR_IAAS_URL);
    }

    public static String generateDefaultDownloadCommand(String rmHostname) {
        return generateNodeDownloadCommand(generateDefaultNodeJarURL(rmHostname));
    }

    public static String generateDefaultNodeJarURL(String rmHostname) {
        return rmHostname + nsConfig.getString(NSProperties.DEFAULT_SUFFIX_RM_TO_NODEJAR_URL);
    }
}
