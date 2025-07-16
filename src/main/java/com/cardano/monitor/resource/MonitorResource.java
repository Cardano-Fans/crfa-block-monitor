package com.cardano.monitor.resource;

import com.cardano.monitor.dto.ActiveRequest;
import com.cardano.monitor.dto.ControlRequest;
import com.cardano.monitor.dto.HealthResponse;
import com.cardano.monitor.dto.StatusResponse;
import com.cardano.monitor.model.*;
import com.cardano.monitor.service.BlockProducerMonitorServiceIF;
import com.cardano.monitor.service.DnsServiceIF;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.time.Instant;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Monitor", description = "Block producer monitoring and failover operations")
public class MonitorResource {
    
    @Inject
    BlockProducerMonitorServiceIF monitorService;
    
    @Inject
    DnsServiceIF dnsService;
    
    @GET
    @Path("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the monitoring service")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Service is healthy", 
                    content = @Content(schema = @Schema(implementation = HealthResponse.class))),
        @APIResponse(responseCode = "500", description = "Service is unhealthy",
                    content = @Content(schema = @Schema(implementation = HealthResponse.class)))
    })
    public Response health() {
        ServerStatus status = monitorService.getStatus();

        HealthStatus healthStatus = status.daemonStatus() == DaemonStatus.RUNNING ? 
            HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;

        HealthResponse body = new HealthResponse(healthStatus, Instant.now());

        // Return 200 if healthy, 500 if unhealthy
        int statusCode = (healthStatus == HealthStatus.HEALTHY) ? 200 : 500;

        return Response.status(statusCode).entity(body).build();
    }

    @GET
    @Path("/status")
    @Operation(summary = "Get monitoring status", description = "Returns detailed status of primary and secondary servers")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = StatusResponse.class)))
    })
    public StatusResponse status() {
        ServerStatus status = monitorService.getStatus();

        return new StatusResponse(Instant.now(), status);
    }
    
    @POST
    @Path("/control")
    @Operation(summary = "Control monitoring", description = "Start or stop the monitoring service")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Control command executed successfully",
                    content = @Content(schema = @Schema(implementation = com.cardano.monitor.model.ApiResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid action provided",
                    content = @Content(schema = @Schema(implementation = com.cardano.monitor.model.ApiResponse.class)))
    })
    public com.cardano.monitor.model.ApiResponse control(@Valid @Parameter(description = "Control action (START or STOP)") ControlRequest request) {
        return switch (request.action()) {
            case START -> monitorService.start();
            case STOP -> monitorService.stop();

            default -> com.cardano.monitor.model.ApiResponse.error("Invalid action. Use 'START' or 'STOP'");
        };
    }
    
    @POST
    @Path("/active")
    @Operation(summary = "Switch active server", description = "Manually switch between primary and secondary servers")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Server switch executed successfully",
                    content = @Content(schema = @Schema(implementation = com.cardano.monitor.model.ApiResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid server type provided",
                    content = @Content(schema = @Schema(implementation = com.cardano.monitor.model.ApiResponse.class)))
    })
    public com.cardano.monitor.model.ApiResponse active(@Valid @Parameter(description = "Server to make active (PRIMARY or SECONDARY)") ActiveRequest request) {
        if (request.active() != null) {
            return monitorService.manualSwitch(request.active());
        }

        return com.cardano.monitor.model.ApiResponse.error("Invalid request. Use 'ACTIVE' field with 'PRIMARY' or 'SECONDARY'");
   }
   
   @GET
   @Path("/dns/current")
   @Operation(summary = "Get current DNS record", description = "Returns the current DNS record IP and active server type")
   @APIResponses({
       @APIResponse(responseCode = "200", description = "DNS record retrieved successfully",
                   content = @Content(schema = @Schema(implementation = DnsRecordResponse.class))),
       @APIResponse(responseCode = "503", description = "Failed to read DNS record",
                   content = @Content(schema = @Schema(implementation = com.cardano.monitor.model.ApiResponse.class)))
   })
   public Response getCurrentDnsRecord() {
       String currentIp = dnsService.getCurrentDnsRecordIp();
       ServerType activeServer = dnsService.detectCurrentActiveServer();
       
       if (currentIp != null) {
           return Response.ok(new DnsRecordResponse(currentIp, activeServer)).build();
       } else {
           return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                   .entity(com.cardano.monitor.model.ApiResponse.error("Failed to read DNS record"))
                   .build();
       }
   }
   
   public record DnsRecordResponse(String currentIp, ServerType activeServer) {}

}