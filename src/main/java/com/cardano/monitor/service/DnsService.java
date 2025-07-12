package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.ServerType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
@Slf4j
public class DnsService {

    @Inject
    MonitorConfig config;
    
    @RestClient
    @Inject
    NameComApiClient nameComClient;
    
    public boolean switchDnsToServer(ServerType serverType) {
        if (serverType == ServerType.NONE) {
            log.error("Cannot switch DNS to NONE - no server specified");
            return false;
        }
        
        try {
            MonitorConfig.ServerConfig serverConfig = switch (serverType) {
                case PRIMARY -> config.primary();
                case SECONDARY -> config.secondary();
                case NONE -> throw new IllegalArgumentException("NONE is not a valid DNS target");
            };
            
            MonitorConfig.DnsConfig dnsConfig = config.dns();
            
            DnsUpdateRequest request = new DnsUpdateRequest(
                dnsConfig.recordHost(),
                dnsConfig.recordFqdn(),
                dnsConfig.recordType(),
                serverConfig.host(),
                dnsConfig.recordTtl()
            );
            
            log.info("Switching DNS to {} ({}: {})", serverType, serverConfig.name(), serverConfig.host());
            
            String auth = java.util.Base64.getEncoder().encodeToString(
                (dnsConfig.username() + ":" + dnsConfig.password()).getBytes()
            );

            try (Response response = nameComClient.updateDnsRecord(
                    "Basic " + auth,
                    dnsConfig.domain(),
                    dnsConfig.recordId(),
                    request
            )) {
                if (response.getStatus() == 200) {
                    log.info("Successfully switched DNS to {}", serverType);
                    return true;
                } else {
                    log.error("Failed to switch DNS to {}: HTTP {}", serverType, response.getStatus());
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Error switching DNS to {}", serverType, e);
            return false;
        }
    }
    
    public record DnsUpdateRequest(
        String host,
        String fqdn,
        String type,
        String answer,
        int ttl
    ) {}
    
    @RegisterRestClient(configKey = "name-com-api")
    @Path("/v4/domains")
    public interface NameComApiClient {
        
        @PUT
        @Path("/{domain}/records/{recordId}")
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.APPLICATION_JSON)
        Response updateDnsRecord(
            @HeaderParam("Authorization") String authorization,
            @PathParam("domain") String domain,
            @PathParam("recordId") String recordId,
            DnsUpdateRequest request
        );
    }

}
