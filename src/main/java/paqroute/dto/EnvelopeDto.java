package paqroute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnvelopeDto {
    private String transportVersion = "1";
    private String runId;
    private int seq;
    private String sentAt;
    private String kind; // "plan" o "run-event"
    
    // Solo si kind == "plan"
    private String payloadVersion;
    private Object payload; // IterationPlan
    
    // Solo si kind == "run-event"
    private RunEventDto event;

    public EnvelopeDto() {}

    // getters y setters

    public String getTransportVersion() { return transportVersion; }
    public void setTransportVersion(String transportVersion) { this.transportVersion = transportVersion; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getPayloadVersion() { return payloadVersion; }
    public void setPayloadVersion(String payloadVersion) { this.payloadVersion = payloadVersion; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public RunEventDto getEvent() { return event; }
    public void setEvent(RunEventDto event) { this.event = event; }
}
