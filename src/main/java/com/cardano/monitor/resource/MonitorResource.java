package com.cardano.monitor.resource;

import com.cardano.monitor.dto.ActiveRequest;
import com.cardano.monitor.dto.ControlRequest;
import com.cardano.monitor.dto.HealthResponse;
import com.cardano.monitor.dto.StatusResponse;
import com.cardano.monitor.model.*;
import com.cardano.monitor.service.BlockProducerMonitorService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MonitorResource {
    
    @Inject
    BlockProducerMonitorService monitorService;
    
    @GET
    @Path("/health")
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
    public StatusResponse status() {
        ServerStatus status = monitorService.checkServers();

        return new StatusResponse(Instant.now(), status);
    }
    
    @POST
    @Path("/control")
    public ApiResponse control(@Valid ControlRequest request) {
        return switch (request.action()) {
            case START -> monitorService.start();
            case STOP -> monitorService.stop();

            default -> ApiResponse.error("Invalid action. Use 'START' or 'STOP'");
        };
    }
    
    @POST
    @Path("/active")
    public ApiResponse active(@Valid ActiveRequest request) {
        if (request.active() != null) {
            return monitorService.manualSwitch(request.active());
        }

        return ApiResponse.error("Invalid request. Use 'ACTIVE' field with 'PRIMARY' or 'SECONDARY'");
   }

}