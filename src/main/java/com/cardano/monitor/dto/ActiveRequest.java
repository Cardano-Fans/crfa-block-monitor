package com.cardano.monitor.dto;

import com.cardano.monitor.model.ServerType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;

public record ActiveRequest(
    
    @JsonProperty("active")
    ServerType active
) {
    @AssertTrue(message = "'active' must be specified with 'PRIMARY' or 'SECONDARY'")
    public boolean isValid() {
        return active != null;
    }

}