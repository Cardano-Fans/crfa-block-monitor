package com.cardano.monitor.dto;

import com.cardano.monitor.model.MonitorAction;
import com.cardano.monitor.serialization.MonitorActionDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

public record ControlRequest(
    @JsonProperty("action")
    @JsonDeserialize(using = MonitorActionDeserializer.class)
    @NotNull(message = "Action is required")
    MonitorAction action
) {}