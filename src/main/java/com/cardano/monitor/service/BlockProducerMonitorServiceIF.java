package com.cardano.monitor.service;

import com.cardano.monitor.model.ApiResponse;
import com.cardano.monitor.model.ServerStatus;
import com.cardano.monitor.model.ServerType;

public interface BlockProducerMonitorServiceIF {
    
    ServerStatus checkServers();
    
    ApiResponse start();
    
    ApiResponse stop();
    
    ApiResponse manualSwitch(ServerType targetServer);
    
    void resetState();
    
    boolean isRunning();
    
    ServerStatus getStatus();
}