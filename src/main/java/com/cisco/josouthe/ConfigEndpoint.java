package com.cisco.josouthe;

import java.util.HashMap;
import java.util.Map;

public class ConfigEndpoint {
    public String name, url, user, password;
    public SNMPEndpoint snmpEndpoint = new SNMPEndpoint();

    public SNMPEndpoint getSnmpEndpoint() { return snmpEndpoint; }

    public class SNMPEndpoint {
        public String targetAddress="unconfigured", version="2", communityName="public", contextName="", securityName, authPassphrase, authProtocol="hmac384sha512", privPassphrase, privProtocol="aes256";
        public Map<String,String> oids = new HashMap<String,String>(){{
            put(".1.3.6.1.4.1.2021.10.1.3.1", "1 minute load average");
            put(".1.3.6.1.4.1.2021.10.1.3.2", "5 minute load average");
            put(".1.3.6.1.4.1.2021.10.1.3.3", "15 minute load average");
            put(".1.3.6.1.4.1.2021.11.11.0", "CPU Idle %");
            put(".1.3.6.1.4.1.2021.11.9.0", "CPU User %");
            put(".1.3.6.1.4.1.2021.4.4.0", "Swap Available");
            put(".1.3.6.1.4.1.2021.4.3.0", "Swap Total");
        }};
    }
}
