package com.cardano.monitor.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.time.Duration;

@ConfigMapping(prefix = "monitor")
public interface MonitorConfig {
    
    @WithName("primary")
    ServerConfig primary();
    
    @WithName("secondary")
    ServerConfig secondary();
    
    @WithName("dns")
    DnsConfig dns();
    
    @WithName("timing")
    TimingConfig timing();
    
    interface ServerConfig {
        String name();
        String host();
        int port();
    }
    
    interface DnsConfig {
        @WithName("api-base-url")
        String apiBaseUrl();
        String username();
        String password();
        String domain();
        @WithName("record-id")
        String recordId();
        @WithName("record-host")
        String recordHost();
        @WithName("record-fqdn")
        String recordFqdn();
        @WithName("record-type")
        String recordType();
        @WithName("record-ttl")
        int recordTtl();
    }
    
    interface TimingConfig {
        @WithName("check-interval")
        Duration checkInterval();
        @WithName("failover-delay")
        Duration failoverDelay();
        @WithName("failback-delay")
        Duration failbackDelay();
        @WithName("connection-timeout")
        Duration connectionTimeout();
    }

}
