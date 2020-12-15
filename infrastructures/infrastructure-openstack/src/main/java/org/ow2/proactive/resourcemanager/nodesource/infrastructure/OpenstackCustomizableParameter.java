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


public class OpenstackCustomizableParameter {

    private String image;

    private String vmPublicKeyName;

    private String flavor;

    private Set<String> securityGroupNames;

    private Set<Integer> portsToOpen;

    private String additionalProperties;

    public OpenstackCustomizableParameter(String image, String vmPublicKeyName, String flavor,
            Set<String> securityGroupNames, Set<Integer> portsToOpen, String additionalProperties) {
        this.image = image;
        this.vmPublicKeyName = vmPublicKeyName;
        this.flavor = flavor;
        this.securityGroupNames = securityGroupNames;
        this.portsToOpen = portsToOpen;
        this.additionalProperties = additionalProperties;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getVmPublicKeyName() {
        return vmPublicKeyName;
    }

    public void setVmPublicKeyName(String vmPublicKeyName) {
        this.vmPublicKeyName = vmPublicKeyName;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public Set<String> getSecurityGroupNames() {
        return securityGroupNames;
    }

    public void setSecurityGroupNames(Set<String> securityGroupNames) {
        this.securityGroupNames = securityGroupNames;
    }

    public Set<Integer> getPortsToOpen() {
        return portsToOpen;
    }

    public void setPortsToOpen(Set<Integer> portsToOpen) {
        this.portsToOpen = portsToOpen;
    }

    public String getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(String additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

}
