package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IterationPlanDto {
    private Integer iterationId;
    private String simTimeStart;
    private String simTimeEnd;
    private List<RouteDto> routes;
    private List<UnavailableUnitDto> unavailableUnits;
    private List<ActiveBlockageDto> activeBlockages;
    private List<StockByWarehouseDto> stockByWarehouse;
    private Object terminal; // CollapseDto o CompletedDto

    // Getters and Setters
    public Integer getIterationId() { return iterationId; }
    public void setIterationId(Integer iterationId) { this.iterationId = iterationId; }

    public String getSimTimeStart() { return simTimeStart; }
    public void setSimTimeStart(String simTimeStart) { this.simTimeStart = simTimeStart; }

    public String getSimTimeEnd() { return simTimeEnd; }
    public void setSimTimeEnd(String simTimeEnd) { this.simTimeEnd = simTimeEnd; }

    public List<RouteDto> getRoutes() { return routes; }
    public void setRoutes(List<RouteDto> routes) { this.routes = routes; }

    public List<UnavailableUnitDto> getUnavailableUnits() { return unavailableUnits; }
    public void setUnavailableUnits(List<UnavailableUnitDto> unavailableUnits) { this.unavailableUnits = unavailableUnits; }

    public List<ActiveBlockageDto> getActiveBlockages() { return activeBlockages; }
    public void setActiveBlockages(List<ActiveBlockageDto> activeBlockages) { this.activeBlockages = activeBlockages; }

    public List<StockByWarehouseDto> getStockByWarehouse() { return stockByWarehouse; }
    public void setStockByWarehouse(List<StockByWarehouseDto> stockByWarehouse) { this.stockByWarehouse = stockByWarehouse; }

    public Object getTerminal() { return terminal; }
    public void setTerminal(Object terminal) { this.terminal = terminal; }
}
