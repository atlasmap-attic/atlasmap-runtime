/*
 * Salesforce Query DTO generated by camel-salesforce-maven-plugin
 * Generated on: Mon Mar 02 02:58:34 EST 2015
 */
package org.apache.camel.salesforce.dto;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;

import java.util.List;

/**
 * Salesforce QueryRecords DTO for type LoginHistory
 */
public class QueryRecordsLoginHistory extends AbstractQueryRecordsBase {

    @XStreamImplicit
    private List<LoginHistory> records;

    public List<LoginHistory> getRecords() {
        return records;
    }

    public void setRecords(List<LoginHistory> records) {
        this.records = records;
    }
}
