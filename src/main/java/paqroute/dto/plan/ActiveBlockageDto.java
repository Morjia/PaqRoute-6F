package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveBlockageDto {
    private String blockageId;
    private String windowStart;
    private String windowEnd;
    private List<CoordinateDto> polyline;

    public String getBlockageId() { return blockageId; }
    public void setBlockageId(String blockageId) { this.blockageId = blockageId; }

    public String getWindowStart() { return windowStart; }
    public void setWindowStart(String windowStart) { this.windowStart = windowStart; }

    public String getWindowEnd() { return windowEnd; }
    public void setWindowEnd(String windowEnd) { this.windowEnd = windowEnd; }

    public List<CoordinateDto> getPolyline() { return polyline; }
    public void setPolyline(List<CoordinateDto> polyline) { this.polyline = polyline; }
}
