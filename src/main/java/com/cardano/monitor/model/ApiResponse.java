package com.cardano.monitor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(
    boolean success,
    String message,
    Object data
) {
    
    public static ApiResponse success(String message) {
        return new ApiResponse(true, message, null);
    }
    
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(true, message, data);
    }
    
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null);
    }

}