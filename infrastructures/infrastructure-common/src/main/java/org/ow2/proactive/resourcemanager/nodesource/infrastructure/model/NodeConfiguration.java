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
package org.ow2.proactive.resourcemanager.nodesource.infrastructure.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeConfiguration implements Serializable {

    private String image;

    private String vmType;

    private Integer numberOfCores;

    private Integer amountOfMemory;

    private VmCredentials credentials;

    private Port[] portToOpens;

    private String nodeTags;

    public NodeConfiguration() {
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getVmType() {
        return vmType;
    }

    public void setVmType(String vmType) {
        this.vmType = vmType;
    }

    public Integer getNumberOfCores() {
        return numberOfCores;
    }

    public void setNumberOfCores(Integer numberOfCores) {
        this.numberOfCores = numberOfCores;
    }

    public Integer getAmountOfMemory() {
        return amountOfMemory;
    }

    public void setAmountOfMemory(Integer amountOfMemory) {
        this.amountOfMemory = amountOfMemory;
    }

    public VmCredentials getCredentials() {
        return credentials;
    }

    public void setCredentials(VmCredentials credentials) {
        this.credentials = credentials;
    }

    public Port[] getPortToOpens() {
        return portToOpens;
    }

    public void setPortToOpens(Port[] portToOpens) {
        this.portToOpens = portToOpens;
    }

    public String getNodeTags() {
        return nodeTags;
    }

    public void setNodeTags(String nodeTags) {
        this.nodeTags = nodeTags;
    }
}
