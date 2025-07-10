package com.cardano.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ServerStatus(
    @JsonProperty("daemon_status") DaemonStatus daemonStatus,
    @JsonProperty("current_active") ServerType currentActive,
    @JsonProperty("primary_status") ServerHealthStatus primaryStatus,
    @JsonProperty("secondary_status") ServerHealthStatus secondaryStatus,
    @JsonProperty("last_check") Instant lastCheck,
    @JsonProperty("primary_down_since") Instant primaryDownSince,
    @JsonProperty("primary_up_since") Instant primaryUpSince,
    @JsonProperty("manual_override") boolean manualOverride,
    @JsonProperty("next_action") NextAction.WithContext nextAction,
    @JsonProperty("config") ConfigInfo config
) {
    
    public record ConfigInfo(
        ServerInfo primary,
        ServerInfo secondary
    ) {}
    
    public record ServerInfo(
        String name,
        String host,
        int port
    ) {}
}