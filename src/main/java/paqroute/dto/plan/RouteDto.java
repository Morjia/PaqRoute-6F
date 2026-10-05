package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteDto {
    private String unitId;
    private String routeId;
    private StartAtDto startAt;
    private List<StopDto> stops;

    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public StartAtDto getStartAt() { return startAt; }
    public void setStartAt(StartAtDto startAt) { this.startAt = startAt; }

    public List<StopDto> getStops() { return stops; }
    public void setStops(List<StopDto> stops) { this.stops = stops; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StartAtDto {
        private int x;
        private int y;
        private String atSimTime;

        public int getX() { return x; }
        public void setX(int x) { this.x = x; }

        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public String getAtSimTime() { return atSimTime; }
        public void setAtSimTime(String atSimTime) { this.atSimTime = atSimTime; }
    }
}
