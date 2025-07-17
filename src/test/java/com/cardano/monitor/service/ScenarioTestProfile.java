package com.cardano.monitor.service;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class ScenarioTestProfile implements QuarkusTestProfile {
    
    @Override
    public String getConfigProfile() {
        return "scenario-test";
    }
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of();
    }
}