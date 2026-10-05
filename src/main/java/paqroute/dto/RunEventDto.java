package paqroute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunEventDto {
    private String type; // parameter-changed, incident-applied, etc.
    private String commandId;
    
    // parameter-changed
    private String parameter;
    private Object value;
    private String effectiveFromSimTime;
    
    // incident-applied
    private Map<String, Object> incident;
    
    // command-failed
    private String reason;
    
    // controller-changed
    private String newToken;

    public RunEventDto() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }

    public String getParameter() { return parameter; }
    public void setParameter(String parameter) { this.parameter = parameter; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public String getEffectiveFromSimTime() { return effectiveFromSimTime; }
    public void setEffectiveFromSimTime(String effectiveFromSimTime) { this.effectiveFromSimTime = effectiveFromSimTime; }

    public Map<String, Object> getIncident() { return incident; }
    public void setIncident(Map<String, Object> incident) { this.incident = incident; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNewToken() { return newToken; }
    public void setNewToken(String newToken) { this.newToken = newToken; }
}
