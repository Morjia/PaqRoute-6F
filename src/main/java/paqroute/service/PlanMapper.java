package paqroute.service;

import org.springframework.stereotype.Component;
import paqroute.*;
import paqroute.dto.plan.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PlanMapper {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public IterationPlanDto mapToPlan(
            int iterationId,
            LocalDateTime simTimeStart,
            LocalDateTime simTimeEnd,
            Solucion solucion,
            ContextoPlanificacion ctx,
            List<Vehiculo> flotaCompleta,
            TerminalDto.CollapseDto collapseDto) {

        IterationPlanDto plan = new IterationPlanDto();
        plan.setIterationId(iterationId);
        plan.setSimTimeStart(formatTime(simTimeStart));
        plan.setSimTimeEnd(formatTime(simTimeEnd));

        // 1. Mapear rutas
        List<RouteDto> routes = new ArrayList<>();
        if (solucion != null && solucion.rutas != null) {
            for (Ruta r : solucion.rutas) {
                routes.add(mapRuta(r, simTimeStart, ctx));
            }
        }
        plan.setRoutes(routes);

        // 2. Mapear unidades indispobibles (mantenimiento o avería)
        List<UnavailableUnitDto> unavailableUnits = new ArrayList<>();
        for (Vehiculo v : flotaCompleta) {
            if (v.estado == Vehiculo.Estado.MANTENIMIENTO) {
                UnavailableUnitDto u = new UnavailableUnitDto();
                u.setUnitId(v.id);
                u.setReason("preventivo");
                u.setAvailableFromSimTime(formatTime(v.disponibleDesde));
                unavailableUnits.add(u);
            }
        }
        plan.setUnavailableUnits(unavailableUnits);

        // 3. Mapear bloqueos activos
        List<ActiveBlockageDto> blockages = new ArrayList<>();
        // TODO: Extraer los bloqueos vigentes de ctx o del Simulador.
        // Por el momento se envia vacio para no romper.
        plan.setActiveBlockages(blockages);

        // 4. Mapear stock de almacenes
        List<StockByWarehouseDto> stocks = new ArrayList<>();
        if (ctx != null && ctx.almacenes != null) {
            for (Almacen a : ctx.almacenes) {
                StockByWarehouseDto s = new StockByWarehouseDto();
                s.setWarehouseId(a.id.name().equals("CENTRAL") ? "C" : 
                                 a.id.name().equals("NOROESTE") ? "NO" : "E");
                s.setUnitsAvailable(a.id.name().equals("CENTRAL") ? null : a.stock);
                stocks.add(s);
            }
        }
        plan.setStockByWarehouse(stocks);

        // 5. Terminal (si hubo colapso)
        if (collapseDto != null) {
            TerminalDto t = new TerminalDto();
            t.setCollapse(collapseDto);
            plan.setTerminal(t);
        }

        return plan;
    }

    private RouteDto mapRuta(Ruta r, LocalDateTime simTimeStart, ContextoPlanificacion ctx) {
        RouteDto dto = new RouteDto();
        dto.setUnitId(r.vehiculo.id);
        dto.setRouteId(UUID.randomUUID().toString()); // Se genera un ID opaco
        
        RouteDto.StartAtDto startAt = new RouteDto.StartAtDto();
        startAt.setX(r.almacenDespacho.ubicacion.x);
        startAt.setY(r.almacenDespacho.ubicacion.y);
        startAt.setAtSimTime(formatTime(r.horaInicio != null ? r.horaInicio : simTimeStart));
        dto.setStartAt(startAt);

        List<StopDto> stops = new ArrayList<>();
        
        // Calcular horas de llegada de la secuencia
        List<LocalDateTime> llegadas = RutaUtil.horasLlegada(
            r.secuencia, 
            r.vehiculo.tipo, 
            r.almacenDespacho.ubicacion, 
            simTimeStart, 
            ctx.grafo
        );

        for (int i = 0; i < r.secuencia.size(); i++) {
            Entrega e = r.secuencia.get(i);
            LocalDateTime llegada = llegadas.get(i);
            
            StopDto stop = new StopDto();
            stop.setAt(new CoordinateDto(e.pedido.ubicacion.x, e.pedido.ubicacion.y));
            stop.setEtaSimTime(formatTime(llegada));
            stop.setKind("customer");
            
            ActionDto action = new ActionDto();
            action.setKind("entrega");
            action.setOrderId(e.pedido.idCliente);
            action.setQty(e.cantidad);
            
            stop.setActions(List.of(action));
            stops.add(stop);
        }
        
        dto.setStops(stops);
        return dto;
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) return null;
        return time.atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }
}
