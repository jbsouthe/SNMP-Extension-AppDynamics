package com.cisco.josouthe;


import com.cisco.josouthe.analytics.Analytics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class SNMPMonitor extends AManagedMonitor {
    private Logger logger = LogManager.getFormatterLogger();
    private String metricPrefix = "Custom Metrics|SNMP Monitor|";
    private Analytics analyticsAPIClient = null;

    ConfigEndpoint[] readSNMPConfiguration(String configFileName) throws TaskExecutionException {
        ConfigEndpoint[] endpoints = null;
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            BufferedReader reader = new BufferedReader(new FileReader(configFileName));
            StringBuilder jsonFileContent = new StringBuilder();
            while (reader.ready()) {
                jsonFileContent.append(reader.readLine());
            }
            endpoints = gson.fromJson(jsonFileContent.toString(), ConfigEndpoint[].class);
            if (endpoints != null) return endpoints;
        } catch (IOException exception) {
            logger.warn(String.format("Exception while reading the external file %s, message: %s", configFileName, exception));
        }
        throw new TaskExecutionException("Could not read SNMP Enpoint Configuration JSON File: "+ configFileName);
    }

    @Override
    public TaskOutput execute(Map<String, String> configMap, TaskExecutionContext taskExecutionContext) throws TaskExecutionException {
        this.logger = taskExecutionContext.getLogger();
        ConfigEndpoint[] endpoints = null;
        if( configMap.getOrDefault("configFile","unconfigured").equals("unconfigured") ){
            throw new TaskExecutionException("SNMP Config File Not Set, nothing to do");
        } else {
            endpoints = readSNMPConfiguration(taskExecutionContext.getTaskDir() +"/"+ configMap.get("configFile") );
            if( endpoints == null ) throw new TaskExecutionException("No End Points read from configuration, something must be wrong");
        }
        if( this.analyticsAPIClient == null )
            this.analyticsAPIClient = new Analytics( configMap.get("analytics_URL"), configMap.get("analytics_apiAccountName"), configMap.get("analytics_apiKey"));
        if( configMap.containsKey("metricPrefix") ) metricPrefix = "Custom Metrics|"+ configMap.get("metricPrefix");
        printMetric("up", 1,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );

        for( ConfigEndpoint endpoint : endpoints ) {
            SNMPAPI snmpApiClient = null;
            if ( !"unconfigured".equals(endpoint.snmpEndpoint.targetAddress)) {
                try {
                    snmpApiClient = new SNMPAPI(endpoint.snmpEndpoint, taskExecutionContext);
                } catch (IOException ioException) {
                    logger.warn(String.format("Could not configure SNMP settings, ignoring SNMP entirely :) " + ioException.getMessage()));
                }
            }

            if (snmpApiClient != null) {
                Map<String, String> snmpData = snmpApiClient.getAllData();
                for (String key : snmpData.keySet()) {
                    printMetricCurrent(endpoint.name+"|"+key, snmpData.get(key));
                }
                //snmpApiClient.close();
            }
        }
        return new TaskOutput("SNMP Monitor Metric Upload Complete");
    }

    public void printMetricCurrent(String metricName, Object metricValue) {
        printMetric(metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );
    }
    public void printMetricSum(String metricName, Object metricValue) {
        printMetric(metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );
    }

    public void printMetric(String metricName, Object metricValue, String aggregation, String timeRollup, String cluster)
    {
        if( Utility.isDecimalNumber(String.valueOf(metricValue))){
            metricName += " (x100)";
            metricValue = Utility.decimalToLong(String.valueOf(metricValue));
        }
        logger.info(String.format("Print Metric: '%s%s'=%s",this.metricPrefix, metricName, metricValue));
        MetricWriter metricWriter = getMetricWriter(this.metricPrefix + metricName,
                aggregation,
                timeRollup,
                cluster
        );

        metricWriter.printMetric(String.valueOf(metricValue));
    }
}
