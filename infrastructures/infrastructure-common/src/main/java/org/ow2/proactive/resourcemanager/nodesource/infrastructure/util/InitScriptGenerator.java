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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;

import lombok.Getter;


public class InitScriptGenerator {

    private static final Logger logger = Logger.getLogger(InitScriptGenerator.class);

    protected static Configuration nsConfig;

    protected static WebPropertiesLoader webConfig;

    public static final String NODE_JAR_URL_PROPERTY = "%nodeJarUrl%";

    public static final String PROTOCOL_PROPERTY = "%protocol%";

    public static final String JYTHON_PATH_PROPERTY = "%jythonPath%";

    public static final String RM_HOSTNAME_PROPERTY = "%rmHostname%";

    public static final String INSTANCE_ID_NODE_PROPERTY_PROPERTY = "%instanceIdNodeProperty%";

    public static final String INSTANCE_ID_PROPERTY = "%instanceId%";

    public static final String RM_URL_PROPERTY = "%rmUrl%";

    public static final String NODE_SOURCE_NAME_PROPERTY = "%nodeSourceName%";

    public static final String NODE_NAMING_OPTION_PROPERTY = "%nodeNamingOption%";

    public static final String CREDENTIALS_PROPERTY = "%credentials%";

    public static final String NUMBER_OF_NODES_PER_INSTANCE_PROPERTY = "%numberOfNodesPerInstance%";

    public static final String ADDITIONAL_PROPERTIES_PROPERTY = "%additionalProperties%";

    static {
        try {
            // load configuration manager with the NodeSource properties file
            nsConfig = NSProperties.loadConfig();
            webConfig = new WebPropertiesLoader();
        } catch (ConfigurationException e) {
            logger.error("Exception when loading NodeSource properties", e);
            throw new RuntimeException(e);
        }
    }

    @Getter
    protected String defaultLinuxStartupScript = nsConfig.getString(NSProperties.LINUX_STARTUP_SCRIPT);

    @Getter
    protected String defaultWindowsStartupScript = nsConfig.getString(NSProperties.WINDOWS_STARTUP_SCRIPT);

    public List<String> buildScript(String startupScriptTemplate, String instanceId, String rmUrl, String rmHostname,
            String nodeJarUrl, String instanceIdNodeProperty, String additionalProperties, String nodeSourceName,
            String nodeBaseName, int numberOfNodesPerInstance, String credentials) {
        startupScriptTemplate = startupScriptTemplate.replace(NODE_JAR_URL_PROPERTY, nodeJarUrl);

        String protocol = rmUrl.substring(0, rmUrl.indexOf(':')).trim();
        startupScriptTemplate = startupScriptTemplate.replace(PROTOCOL_PROPERTY, protocol);

        String jythonPath = nsConfig.getString(NSProperties.DEFAULT_JYTHON_PATH);
        startupScriptTemplate = startupScriptTemplate.replace(JYTHON_PATH_PROPERTY, jythonPath);

        startupScriptTemplate = startupScriptTemplate.replace(RM_HOSTNAME_PROPERTY, rmHostname);
        startupScriptTemplate = startupScriptTemplate.replace(INSTANCE_ID_NODE_PROPERTY_PROPERTY,
                                                              instanceIdNodeProperty);
        startupScriptTemplate = startupScriptTemplate.replace(INSTANCE_ID_PROPERTY, instanceId);
        startupScriptTemplate = startupScriptTemplate.replace(RM_URL_PROPERTY, rmUrl);
        startupScriptTemplate = startupScriptTemplate.replace(NODE_SOURCE_NAME_PROPERTY, nodeSourceName);

        String nodeNamingOption = (nodeBaseName == null || nodeBaseName.isEmpty()) ? "" : " -n " + nodeBaseName;
        startupScriptTemplate = startupScriptTemplate.replace(NODE_NAMING_OPTION_PROPERTY, nodeNamingOption);
        startupScriptTemplate = startupScriptTemplate.replace(CREDENTIALS_PROPERTY, credentials);
        startupScriptTemplate = startupScriptTemplate.replace(NUMBER_OF_NODES_PER_INSTANCE_PROPERTY,
                                                              String.valueOf(numberOfNodesPerInstance));
        startupScriptTemplate = startupScriptTemplate.replace(ADDITIONAL_PROPERTIES_PROPERTY, additionalProperties);

        // use the unified line breaker "\n"
        startupScriptTemplate = startupScriptTemplate.replace("\r\n", "\n");
        List<String> startupScript = Arrays.asList(startupScriptTemplate.split("\n"))
                                           .stream()
                                           .filter(s -> !s.isEmpty())
                                           .collect(Collectors.toList());

        logger.info("Node initial script generated: " + startupScript);

        return startupScript;
    }

    public static String generateDefaultIaasConnectorURL(String rmHostname) {
        // I return the requested value while taking into account the configuration parameters
        return generateDefaultBaseURL(rmHostname) + nsConfig.getString(NSProperties.DEFAULT_SUFFIX_CONNECTOR_IAAS_URL);
    }

    public static String generateDefaultNodeJarURL(String rmHostname) {
        return generateDefaultBaseURL(rmHostname) + nsConfig.getString(NSProperties.DEFAULT_SUFFIX_RM_TO_NODEJAR_URL);
    }

    public static String generateDefaultBaseURL(String hostname) {
        return webConfig.getHttpProtocol() + "://" + hostname + ":" + webConfig.getRestPort();
    }
}
