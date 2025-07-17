package com.cardano.monitor.service;

import com.cardano.monitor.model.ServerHealthStatus;
import com.cardano.monitor.model.ServerType;

import java.time.Duration;

public interface NetworkServiceIF {
    
    boolean checkHostPort(String host, int port, Duration timeout);
    
    ServerHealthStatus getServerHealthStatus(ServerType serverType);
}