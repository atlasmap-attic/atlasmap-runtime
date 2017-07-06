/*
 * Salesforce DTO generated by camel-salesforce-maven-plugin
 * Generated on: Mon Mar 02 02:58:34 EST 2015
 */
package org.apache.camel.salesforce.dto;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import org.apache.camel.component.salesforce.api.PicklistEnumConverter;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Salesforce DTO for SObject ProcessInstance
 */
@XStreamAlias("ProcessInstance")
public class ProcessInstance extends AbstractSObjectBase {

    // ProcessDefinitionId
    private String ProcessDefinitionId;

    @JsonProperty("ProcessDefinitionId")
    public String getProcessDefinitionId() {
        return this.ProcessDefinitionId;
    }

    @JsonProperty("ProcessDefinitionId")
    public void setProcessDefinitionId(String ProcessDefinitionId) {
        this.ProcessDefinitionId = ProcessDefinitionId;
    }

    // TargetObjectId
    private String TargetObjectId;

    @JsonProperty("TargetObjectId")
    public String getTargetObjectId() {
        return this.TargetObjectId;
    }

    @JsonProperty("TargetObjectId")
    public void setTargetObjectId(String TargetObjectId) {
        this.TargetObjectId = TargetObjectId;
    }

    // Status
    @XStreamConverter(PicklistEnumConverter.class)
    private StatusEnum Status;

    @JsonProperty("Status")
    public StatusEnum getStatus() {
        return this.Status;
    }

    @JsonProperty("Status")
    public void setStatus(StatusEnum Status) {
        this.Status = Status;
    }

}
