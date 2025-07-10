package com.cardano.monitor.dto;

import com.cardano.monitor.model.MonitorAction;
import com.cardano.monitor.model.ServerType;
import com.cardano.monitor.serialization.MonitorActionDeserializer;
import com.cardano.monitor.serialization.ServerTypeDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;

public record ActiveRequest(

    @JsonProperty("action")

    //TODO remove
    @JsonDeserialize(using = MonitorActionDeserializer.class)
    MonitorAction action,
    
    @JsonProperty("active")

    // TODO remove
    @JsonDeserialize(using = ServerTypeDeserializer.class)
    ServerType active
) {
    @AssertTrue(message = "Either 'action' must be 'clear_override' or 'active' must be specified")
    public boolean isValid() {
        return (action == MonitorAction.CLEAR_OVERRIDE && active == null) || 
               (action == null && active != null);
    }

}