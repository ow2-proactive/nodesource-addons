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

import java.io.File;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.PropertiesBuilderParameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.convert.ListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;


public class NSProperties {

    private NSProperties() {
    }

    private static final Logger LOGGER = Logger.getLogger(NSProperties.class);

    public static final String PROPERTIES_FILE = "NodeSource.properties";

    private static final ListDelimiterHandler DELIMITER = new DefaultListDelimiterHandler(',');

    public static final String JRE_INSTALL = "ns.jre.install";

    public static final String JRE_INSTALL_COMMAND = "ns.script.linux.jre.install.command";

    public static final String JAVA_COMMAND = "ns.script.linux.java.command";

    public static final String DEFAULT_SUFFIX_RM_TO_NODEJAR_URL = "ns.default.suffix.rm.to.nodejar.url";

    public static final String DEFAULT_SUFFIX_CONNECTOR_IAAS_URL = "ns.default.suffix.connector.iaas.url";

    public static final String DEFAULT_PREFIX_CONNECTOR_IAAS_URL = "ns.default.prefix.connector.iaas.url";

    /**
     * loads NodeSource configuration.
     *
     * @return NodeSource configuration
     * @throws ConfigurationException If a problem occurs when loading IAM configuration
     * @since version 8.4.0
     */
    public static Configuration loadConfig() throws ConfigurationException {

        Configuration config;

        File propertiesFile = new File(NSProperties.class.getClassLoader().getResource(PROPERTIES_FILE).getFile());

        PropertiesBuilderParameters propertyParameters = new Parameters().properties();
        propertyParameters.setFile(propertiesFile);
        propertyParameters.setThrowExceptionOnMissing(true);
        propertyParameters.setListDelimiterHandler(DELIMITER);

        FileBasedConfigurationBuilder<PropertiesConfiguration> builder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

        builder.configure(propertyParameters);

        config = builder.getConfiguration();

        LOGGER.debug("NodeSources configuration loaded");

        return config;
    }
}
