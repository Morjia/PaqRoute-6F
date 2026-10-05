package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnavailableUnitDto {
    private String unitId;
    private String reason; // "preventivo", "averia"
    private String availableFromSimTime;

    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getAvailableFromSimTime() { return availableFromSimTime; }
    public void setAvailableFromSimTime(String availableFromSimTime) { this.availableFromSimTime = availableFromSimTime; }
}
