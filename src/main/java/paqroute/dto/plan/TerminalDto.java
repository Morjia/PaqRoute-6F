package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TerminalDto {
    private CollapseDto collapse;
    private CompletedDto completed;
    
    // Getters and Setters
    public CollapseDto getCollapse() { return collapse; }
    public void setCollapse(CollapseDto collapse) { this.collapse = collapse; }
    
    public CompletedDto getCompleted() { return completed; }
    public void setCompleted(CompletedDto completed) { this.completed = completed; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CollapseDto {
        private String simTime;
        private String orderId;
        private String cause; // "incumplimiento", "arranque"
        private IterationPlanDto lastStablePlan;
        
        public String getSimTime() { return simTime; }
        public void setSimTime(String simTime) { this.simTime = simTime; }
        
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public String getCause() { return cause; }
        public void setCause(String cause) { this.cause = cause; }
        
        public IterationPlanDto getLastStablePlan() { return lastStablePlan; }
        public void setLastStablePlan(IterationPlanDto lastStablePlan) { this.lastStablePlan = lastStablePlan; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompletedDto {
        private boolean completed = true;
        private String windowStart;
        private String windowEnd;
        private IterationPlanDto lastStablePlan;
        
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        
        public String getWindowStart() { return windowStart; }
        public void setWindowStart(String windowStart) { this.windowStart = windowStart; }
        
        public String getWindowEnd() { return windowEnd; }
        public void setWindowEnd(String windowEnd) { this.windowEnd = windowEnd; }
        
        public IterationPlanDto getLastStablePlan() { return lastStablePlan; }
        public void setLastStablePlan(IterationPlanDto lastStablePlan) { this.lastStablePlan = lastStablePlan; }
    }
}
