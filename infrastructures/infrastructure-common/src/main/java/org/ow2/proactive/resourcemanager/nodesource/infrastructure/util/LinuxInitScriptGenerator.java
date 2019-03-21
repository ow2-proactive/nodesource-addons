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

    private List<String> commands = new ArrayList<>();

    private static Configuration nsConfig = null;

    public List<String> buildScript(String instanceId, String rmUrlToUse, String rmHostname,
            String instanceTagNodeProperty, String additionalProperties, String nsName, int numberOfNodesPerInstance) {

        loadNSConfig();
        commands.clear();

        if (nsConfig.getBoolean(NSProperties.JRE_INSTALL)) {
            commands.add(nsConfig.getString(NSProperties.JRE_INSTALL_COMMAND));
        }

        commands.add(generateNodeDownloadCommand(rmHostname));

        commands.add(generateNodeStartCommand(instanceId,
                                              rmUrlToUse,
                                              rmHostname,
                                              instanceTagNodeProperty,
                                              additionalProperties,
                                              nsName,
                                              numberOfNodesPerInstance));

        logger.info("Node starting script generated for Linux system: " + commands.toString());

        return commands;
    }

    public String generateNodeDownloadCommand(String rmHostname) {
        return "wget -nv " + rmHostname + ":8080/rest/node.jar";
    }

    private String generateNodeStartCommand(String instanceId, String rmUrlToUse, String rmHostname,
            String instanceTagNodeProperty, String additionalProperties, String nsName, int numberOfNodesPerInstance) {

        String javaCommand = nsConfig.getString(NSProperties.JAVA_COMMAND) + " -jar node.jar";

        String protocol = rmUrlToUse.substring(0, rmUrlToUse.indexOf(':')).trim();

        String javaProperties = " -Dproactive.communication.protocol=" + protocol +
                                " -Dproactive.pamr.router.address=" + rmHostname + " -D" + instanceTagNodeProperty +
                                "=" + instanceId + " " + additionalProperties + " -r " + rmUrlToUse + " -s " + nsName +
                                " -w " + numberOfNodesPerInstance;

        return "nohup " + javaCommand + javaProperties + "  &";
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
