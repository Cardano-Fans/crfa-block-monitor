package com.cardano.monitor.serialization;

import com.cardano.monitor.model.ServerType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class ServerTypeDeserializer extends JsonDeserializer<ServerType> {
    
    @Override
    public ServerType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null) {
            return null;
        }
        
        return ServerType.valueOf(value.toUpperCase());
    }
}