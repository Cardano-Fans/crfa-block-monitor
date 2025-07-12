package com.cardano.monitor.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the next action or current state of the automatic monitoring system.
 * This enum provides insight into what the monitor is currently doing or planning to do.
 */
public enum NextAction {
    
    /**
     * Normal operation - everything is stable and no action is needed.
     * Both servers are healthy and the system is operating normally.
     */
    NONE("none"),
    
    /**
     * Waiting for failover delay to expire before switching to secondary.
     * Primary server is down, secondary is available, but we're waiting
     * for the configured failover delay to prevent flapping.
     */
    WAITING_FOR_FAILOVER("waiting_for_failover"),
    
    /**
     * Waiting for failback delay to expire before switching back to primary.
     * Primary server is back up and we're currently using secondary,
     * but we're waiting for the configured failback delay to ensure stability.
     */
    WAITING_FOR_FAILBACK("waiting_for_failback"),
    
    /**
     * Successfully switched DNS to secondary server.
     * This indicates that a failover operation has just completed.
     */
    SWITCHED_TO_SECONDARY("switched_to_secondary"),
    
    /**
     * Successfully switched DNS back to primary server.
     * This indicates that a failback operation has just completed.
     */
    SWITCHED_TO_PRIMARY("switched_to_primary"),
    
    /**
     * Failed to switch DNS to secondary server.
     * Attempted failover but the DNS update operation failed.
     * This requires manual intervention to resolve.
     */
    FAILED_TO_SWITCH_TO_SECONDARY("failed_to_switch_to_secondary"),
    
    /**
     * Failed to switch DNS back to primary server.
     * Attempted failback but the DNS update operation failed.
     * This requires manual intervention to resolve.
     */
    FAILED_TO_SWITCH_TO_PRIMARY("failed_to_switch_to_primary"),
    
    /**
     * Critical error - both servers are down.
     * Primary server is down and secondary server is also unavailable.
     * This is a critical situation requiring immediate attention.
     */
    BOTH_SERVERS_DOWN("both_servers_down"),
    
    /**
     * Secondary server is down while we're using it.
     * We're currently routing traffic to secondary but it's not responding.
     * This is a critical situation as we have no healthy servers active.
     */
    SECONDARY_SERVER_DOWN("secondary_server_down");
    
    private final String value;
    
    NextAction(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    /**
     * Creates a NextAction with additional context information.
     * Used for time-based actions that need to show remaining time.
     */
    public static class WithContext {
        private final NextAction action;
        private final String context;
        
        public WithContext(NextAction action, String context) {
            this.action = action;
            this.context = context;
        }
        
        public WithContext(NextAction action) {
            this.action = action;
            this.context = null;
        }
        
        public NextAction getAction() {
            return action;
        }
        
        public String getContext() {
            return context;
        }
        
        @JsonValue
        public String getValue() {
            if (context != null) {
                return action.getValue() + " (" + context + ")";
            }
            return action.getValue();
        }
        
        @Override
        public String toString() {
            return getValue();
        }
    }
    
    /**
     * Creates a NextAction with remaining time context.
     * 
     * @param remainingSeconds the number of seconds remaining
     * @return NextAction with time context
     */
    public WithContext withRemainingTime(long remainingSeconds) {
        return new WithContext(this, remainingSeconds + "s remaining");
    }
    
    /**
     * Creates a NextAction without additional context.
     * 
     * @return NextAction without context
     */
    public WithContext withoutContext() {
        return new WithContext(this);
    }
}