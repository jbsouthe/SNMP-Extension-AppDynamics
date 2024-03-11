package com.cisco.josouthe;

import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.fluent.SnmpBuilder;
import org.snmp4j.fluent.SnmpCompletableFuture;
import org.snmp4j.fluent.TargetBuilder;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SNMPAPI {
    private Logger logger = LogManager.getFormatterLogger();
    private TransportMapping transportMapping;
    private Snmp snmp;
    private TargetBuilder<?> targetBuilder;
    private Address address;
    private String contextName;
    private String communityName;
    private Map<String,String> oidMap;
    private int timeout=5000;
    private int retries=3;
    private int snmpVersion;

    public SNMPAPI(ConfigEndpoint.SNMPEndpoint snmpEndpoint, TaskExecutionContext taskExecutionContext ) throws TaskExecutionException, IOException {
        if( taskExecutionContext != null ) this.logger=taskExecutionContext.getLogger();
        this.address = GenericAddress.parse(snmpEndpoint.targetAddress);
        this.communityName = snmpEndpoint.communityName;
        SnmpBuilder snmpBuilder = new SnmpBuilder().udp().threads(2);
        switch(snmpEndpoint.version.charAt(0)) {
            case '1':{
                this.snmpVersion = SnmpConstants.version1;
                throw new TaskExecutionException("SNMP Version not yet implemented: "+ snmpEndpoint.version);
            }
            case '2':{
                this.snmpVersion = SnmpConstants.version2c;
                if( "".equals(communityName) ) throw new TaskExecutionException("SNMP v2 being used but Community Name Parameter is null?");
                snmp = snmpBuilder.v2c().build();
                targetBuilder = snmpBuilder.v2c().target(address)
                        .community(new OctetString(communityName))
                        .timeout(timeout).retries(retries);
                break;
            }
            case '3': {
                this.snmpVersion = SnmpConstants.version3;
                List<String> errorParameters = new ArrayList<>();
                if( "".equals(snmpEndpoint.securityName) ) errorParameters.add("Security Name");
                if( errorParameters.size() > 0 ) {
                    StringBuilder sb = new StringBuilder("SNMP v3 being used but missing needed parameter(s): ");
                    for( String param : errorParameters ) sb.append(param+",");
                    sb.deleteCharAt(sb.lastIndexOf(","));
                    throw new TaskExecutionException(sb.toString());
                }
                snmp = snmpBuilder.v3().usm().build();
                byte[] targetEngineID = snmp.discoverAuthoritativeEngineID(address, timeout);
                if( targetEngineID == null ) throw new TaskExecutionException("Could not discover the SNMP Authoritative Engine");
                targetBuilder = snmpBuilder.v3().target(address);
                TargetBuilder<?>.DirectUserBuilder directUserBuilder = targetBuilder.user(snmpEndpoint.securityName, targetEngineID);
                if( snmpEndpoint.authPassphrase != null )
                    directUserBuilder = directUserBuilder.auth(getAuthProtocol(snmpEndpoint.authProtocol)).authPassphrase(snmpEndpoint.authPassphrase);
                if( snmpEndpoint.privPassphrase != null )
                    directUserBuilder = directUserBuilder.priv(getPrivProtocol(snmpEndpoint.privProtocol)).privPassphrase(snmpEndpoint.privPassphrase);
                targetBuilder = directUserBuilder.done().timeout(timeout).retries(retries);
                this.contextName = snmpEndpoint.contextName;
                break;
            }
            default: throw new TaskExecutionException("Unknown SNMP Version? "+ snmpEndpoint.version);
        }
        snmp.listen();
        this.oidMap = snmpEndpoint.oids;
        if(logger.isDebugEnabled()) {
            Target<?> target = targetBuilder.build();
            target.setVersion(this.snmpVersion);
            logger.debug(String.format("snmp target(%s): %s", getVersionString(target.getVersion()), target.toString()));
        }
        logger.debug(String.format("Initialized SNMP API for version %s",snmpEndpoint.version));
    }


    private String getVersionString( int v ) {
        switch (v) {
            case SnmpConstants.version1: return "v1";
            case SnmpConstants.version2c: return "v2c";
            case SnmpConstants.version3: return "v3";
            default: return "unknown-version";
        }
    }

    private TargetBuilder.AuthProtocol getAuthProtocol( String name ) throws TaskExecutionException {
        try {
            return TargetBuilder.AuthProtocol.valueOf(name);
        } catch (IllegalArgumentException exception ) {
            throw new TaskExecutionException("No AuthProtocol found for input name: "+ name);
        }
    }

    private TargetBuilder.PrivProtocol getPrivProtocol( String name ) throws TaskExecutionException {
        try {
            return TargetBuilder.PrivProtocol.valueOf(name);
        } catch (IllegalArgumentException exception ) {
            throw new TaskExecutionException("No PrivProtocol found for input name: "+ name);
        }
    }

    public Map<String,String> getAllData() throws TaskExecutionException {
        Map<String,String> data = new HashMap<>();
        for( String oid : this.oidMap.keySet())  {
            List<VariableBinding> variableBindings = getOID(oid);
            if( variableBindings == null || variableBindings.isEmpty() ) {
                logger.warn("No data returned from snmp request");
                continue;
            }
            VariableBinding variableBinding = variableBindings.get(0);
            logger.debug(String.format("SNMP Data: requested '%s(%s)' returned %s=%s", this.oidMap.get(oid), oid,  getOIDMetricName(variableBinding.getOid().toString()),variableBinding.toValueString()));
            data.put( getOIDMetricName(variableBinding.getOid().toString()), variableBinding.toValueString());
        }
        return data;
    }

    public List<VariableBinding> getOID( String oid ) throws TaskExecutionException {
        logger.debug(String.format("getOIDs beginning(1): %s", oid));
        PDU pdu = null;
        Target<?> target = this.targetBuilder.build();
        target.setVersion(this.snmpVersion);
        logger.debug(String.format("Target version %s for target: %s",target.getVersion(),target.toString()));
        switch (target.getVersion()) {
            case SnmpConstants.version1:
            case SnmpConstants.version2c: {
                pdu = targetBuilder
                        .pdu()
                        .type(PDU.GET)
                        .build();
                break;
            }
            case SnmpConstants.version3: {
                pdu = targetBuilder
                        .pdu()
                        //.type(PDU.GETNEXT)
                        .type(PDU.GET)
                        .contextName(contextName)
                        .build();
                break;
            }
            default: {
                throw new TaskExecutionException(String.format("SNMP version not yet implemented: %s",target.getVersion()));
            }
        }
        //for( String oidName : oids ) {
            pdu.addOID( new VariableBinding( new OID(oid)));
        //}
        logger.debug(String.format("Request PDU: %s", pdu));
        SnmpCompletableFuture snmpRequestFuture = SnmpCompletableFuture.send(snmp, target, pdu);
        logger.debug(String.format("SnmpCompletableFuture created: %s",snmpRequestFuture.toString()));
        try {
            PDU responsePDU = snmpRequestFuture.get();
            logger.debug(String.format("ResponsePDU: %s SnmpCompletableFuture: %s",responsePDU, snmpRequestFuture));
            if( responsePDU.getErrorStatus() != PDU.noError ) {
                logger.warn(String.format("Response returned error: %s",responsePDU.getErrorStatusText()));
                throw new TaskExecutionException(responsePDU.getErrorStatusText());
            }
            List<VariableBinding> vbs = responsePDU.getAll();
            logger.debug(String.format("List<VariableBinding> returned with size: %d",(vbs==null?0:vbs.size())));
            return vbs;
        } catch (Exception ex) {
            if (ex.getCause() != null) {
                logger.warn(String.format("Error in processing: %s",ex.getCause().getMessage(),ex.getCause()));
                throw new TaskExecutionException(ex.getCause().getMessage());
            } else {
                throw new TaskExecutionException(ex.getMessage());
            }
        }
    }

    public String getOIDMetricName( String oid ) {
        logger.debug(String.format("getOIDMetricName: oidmap(size:%d) lookup contains '%s'? '%s' is '%s'",this.oidMap.size(), oid, this.oidMap.containsKey(oid), String.valueOf(this.oidMap.get(oid))));
        String name = this.oidMap.get(oid);
        if( name == null ) name = this.oidMap.get("."+oid);
        return name;
    }

    public void close() {
        try {
            snmp.close();
        } catch (IOException ignore) {
            //ignored
        }
    }
}
