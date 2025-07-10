package com.cardano.monitor.dto;

import com.cardano.monitor.model.ServerStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record StatusResponse(
    @JsonProperty("timestamp")
    Instant timestamp,
    
    @JsonProperty("monitor")
    ServerStatus monitor
) {}