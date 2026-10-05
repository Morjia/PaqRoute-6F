package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopDto {
    private CoordinateDto at;
    private String etaSimTime;
    private String kind; // "warehouse" o "customer"
    private List<ActionDto> actions;

    public CoordinateDto getAt() { return at; }
    public void setAt(CoordinateDto at) { this.at = at; }

    public String getEtaSimTime() { return etaSimTime; }
    public void setEtaSimTime(String etaSimTime) { this.etaSimTime = etaSimTime; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public List<ActionDto> getActions() { return actions; }
    public void setActions(List<ActionDto> actions) { this.actions = actions; }
}
