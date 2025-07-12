package com.cardano.monitor.dto;

import com.cardano.monitor.model.MonitorAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ControlRequest(
    @JsonProperty("action")
    @NotNull(message = "Action is required")
    MonitorAction action
) {}