package com.cisco.josouthe.analytics;

import java.util.Map;

public interface SchemaData {

    public Schema getSchemaDefinition() throws AnalyticsSchemaException;
    public Map<String,String> getSchemaData();
}
