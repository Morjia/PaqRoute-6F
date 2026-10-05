package paqroute.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionDto {
    private String kind; // "entrega", "recarga", "trasvase", "teleport", "averia"
    
    // Para entrega
    private String orderId;
    private Integer qty;
    
    // Para recarga
    private String warehouseId;
    
    // Para trasvase
    private String fromUnitId;
    private String toUnitId;
    private Integer durationMinutes;
    
    // Para teleport
    private CoordinateDto from;
    private String to; // Siempre "C" segun el schema
    
    // Para averia
    private Integer type;
    private CoordinateDto at;
    private String strandedUntilSimTime;

    // Getters and setters
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    
    public String getWarehouseId() { return warehouseId; }
    public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
    
    public String getFromUnitId() { return fromUnitId; }
    public void setFromUnitId(String fromUnitId) { this.fromUnitId = fromUnitId; }
    
    public String getToUnitId() { return toUnitId; }
    public void setToUnitId(String toUnitId) { this.toUnitId = toUnitId; }
    
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    
    public CoordinateDto getFrom() { return from; }
    public void setFrom(CoordinateDto from) { this.from = from; }
    
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    
    public CoordinateDto getAt() { return at; }
    public void setAt(CoordinateDto at) { this.at = at; }
    
    public String getStrandedUntilSimTime() { return strandedUntilSimTime; }
    public void setStrandedUntilSimTime(String strandedUntilSimTime) { this.strandedUntilSimTime = strandedUntilSimTime; }
}
