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

import java.util.Set;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class AzureCustomizableParameter {

    private String image;

    private String imageOSType;

    private String vmSizeType;

    private String vmUsername;

    private String vmPassword;

    private String vmPublicKey;

    private String resourceGroup;

    private String region;

    private String privateNetworkCIDR;

    private Boolean staticPublicIP;

    private Set<Integer> portsToOpen;

    private String additionalProperties;

    public AzureCustomizableParameter(String image, String imageOSType, String vmSizeType, String vmUsername,
            String vmPassword, String vmPublicKey, String resourceGroup, String region, String privateNetworkCIDR,
            Boolean staticPublicIP, Set<Integer> portsToOpen, String additionalProperties) {
        this.image = image;
        this.imageOSType = imageOSType;
        this.vmSizeType = vmSizeType;
        this.vmUsername = vmUsername;
        this.vmPassword = vmPassword;
        this.vmPublicKey = vmPublicKey;
        this.resourceGroup = resourceGroup;
        this.region = region;
        this.privateNetworkCIDR = privateNetworkCIDR;
        this.staticPublicIP = staticPublicIP;
        this.portsToOpen = portsToOpen;
        this.additionalProperties = additionalProperties;
    }
}
