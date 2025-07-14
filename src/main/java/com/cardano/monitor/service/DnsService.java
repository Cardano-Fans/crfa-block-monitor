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

import java.nio.charset.StandardCharsets;

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
                (dnsConfig.username() + ":" + dnsConfig.password()).getBytes(StandardCharsets.UTF_8)
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
                }

                log.error("Failed to switch DNS to {}: HTTP {}", serverType, response.getStatus());
                return false;
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
    
    public String getCurrentDnsRecordIp() {
        try {
            MonitorConfig.DnsConfig dnsConfig = config.dns();
            
            String auth = java.util.Base64.getEncoder().encodeToString(
                (dnsConfig.username() + ":" + dnsConfig.password()).getBytes(StandardCharsets.UTF_8)
            );

            try (Response response = nameComClient.getDnsRecord(
                    "Basic " + auth,
                    dnsConfig.domain(),
                    dnsConfig.recordId()
            )) {
                if (response.getStatus() == 200) {
                    String responseBody = response.readEntity(String.class);
                    
                    // Parse JSON response to extract the answer field
                    if (responseBody.contains("\"answer\"")) {
                        int start = responseBody.indexOf("\"answer\":\"") + 10;
                        int end = responseBody.indexOf("\"", start);
                        if (start > 9 && end > start) {
                            String currentIp = responseBody.substring(start, end);
                            log.info("Current DNS record IP: {}", currentIp);
                            return currentIp;
                        }
                    }
                }
                
                log.error("Failed to read DNS record: HTTP {}", response.getStatus());
                return null;
            }
        } catch (Exception e) {
            log.error("Error reading DNS record", e);
            return null;
        }
    }
    
    public ServerType detectCurrentActiveServer() {
        String currentDnsIp = getCurrentDnsRecordIp();
        
        if (currentDnsIp == null) {
            log.warn("Could not determine current DNS IP, defaulting to PRIMARY");
            return ServerType.PRIMARY;
        }
        
        String primaryIp = config.primary().host();
        String secondaryIp = config.secondary().host();
        
        if (currentDnsIp.equals(primaryIp)) {
            log.info("DNS currently points to PRIMARY server ({})", primaryIp);
            return ServerType.PRIMARY;
        } else if (currentDnsIp.equals(secondaryIp)) {
            log.info("DNS currently points to SECONDARY server ({})", secondaryIp);
            return ServerType.SECONDARY;
        } else {
            log.warn("DNS points to unknown IP ({}) - not matching PRIMARY ({}) or SECONDARY ({}), defaulting to PRIMARY", 
                    currentDnsIp, primaryIp, secondaryIp);
            return ServerType.PRIMARY;
        }
    }
    
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
        
        @GET
        @Path("/{domain}/records/{recordId}")
        @Produces(MediaType.APPLICATION_JSON)
        Response getDnsRecord(
            @HeaderParam("Authorization") String authorization,
            @PathParam("domain") String domain,
            @PathParam("recordId") String recordId
        );
    }

}
