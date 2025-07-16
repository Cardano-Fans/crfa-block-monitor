package com.cardano.monitor.service;

import com.cardano.monitor.model.ServerType;

public interface DnsServiceIF {
    
    boolean switchDnsToServer(ServerType serverType);
    
    String getCurrentDnsRecordIp();
    
    ServerType detectCurrentActiveServer();
}