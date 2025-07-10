package com.cardano.monitor.serialization;

import com.cardano.monitor.model.MonitorAction;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class MonitorActionDeserializer extends JsonDeserializer<MonitorAction> {
    
    @Override
    public MonitorAction deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();

        if (value == null) {
            return null;
        }

        return MonitorAction.valueOf(value.toUpperCase());
    }

}