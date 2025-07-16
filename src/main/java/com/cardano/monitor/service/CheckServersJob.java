package com.cardano.monitor.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CheckServersJob {
    
    @Inject
    BlockProducerMonitorServiceIF monitorService;
    
    @Scheduled(every = "60s")
    public void checkServers() {
        if (monitorService.isRunning()) {
            monitorService.checkServers();
        }
    }
}