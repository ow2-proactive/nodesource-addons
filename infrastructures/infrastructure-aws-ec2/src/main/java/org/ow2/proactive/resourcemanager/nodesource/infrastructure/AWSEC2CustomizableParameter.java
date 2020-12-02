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


public class AWSEC2CustomizableParameter {

    private String image;

    private String vmUsername;

    private String vmKeyPairName;

    private String vmPrivateKey;

    private int ram;

    private int cores;

    private String vmType;

    private String securityGroupIds;

    private Set<Integer> portsToOpen;

    private String additionalProperties;

    public AWSEC2CustomizableParameter(String image, String vmUsername, String vmKeyPairName, String vmPrivateKey,
            int ram, int cores, String vmType, String securityGroupIds, Set<Integer> portsToOpen,
            String additionalProperties) {
        this.image = image;
        this.vmUsername = vmUsername;
        this.vmKeyPairName = vmKeyPairName;
        this.vmPrivateKey = vmPrivateKey;
        this.ram = ram;
        this.cores = cores;
        this.vmType = vmType;
        this.securityGroupIds = securityGroupIds;
        this.portsToOpen = portsToOpen;
        this.additionalProperties = additionalProperties;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getVmUsername() {
        return vmUsername;
    }

    public void setVmUsername(String vmUsername) {
        this.vmUsername = vmUsername;
    }

    public String getVmKeyPairName() {
        return vmKeyPairName;
    }

    public void setVmKeyPairName(String vmKeyPairName) {
        this.vmKeyPairName = vmKeyPairName;
    }

    public String getVmPrivateKey() {
        return vmPrivateKey;
    }

    public void setVmPrivateKey(String vmPrivateKey) {
        this.vmPrivateKey = vmPrivateKey;
    }

    public int getRam() {
        return ram;
    }

    public void setRam(int ram) {
        this.ram = ram;
    }

    public int getCores() {
        return cores;
    }

    public void setCores(int cores) {
        this.cores = cores;
    }

    public String getVmType() {
        return vmType;
    }

    public void setVmType(String vmType) {
        this.vmType = vmType;
    }

    public String getSecurityGroupIds() {
        return securityGroupIds;
    }

    public void setSecurityGroupIds(String securityGroupIds) {
        this.securityGroupIds = securityGroupIds;
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
