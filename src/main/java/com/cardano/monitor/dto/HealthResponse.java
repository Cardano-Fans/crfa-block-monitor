package com.cardano.monitor.dto;

import com.cardano.monitor.model.HealthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

@RegisterForReflection
public record HealthResponse(
    @JsonProperty("status")
    HealthStatus status,
    
    @JsonProperty("timestamp")
    Instant timestamp
) {}