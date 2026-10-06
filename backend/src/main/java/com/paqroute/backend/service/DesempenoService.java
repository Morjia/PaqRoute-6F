package com.paqroute.backend.service;

import com.paqroute.backend.dto.response.KpiResponseDTO;
import com.paqroute.backend.model.HistorialSimulacion;
import com.paqroute.backend.repository.HistorialSimulacionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Indicadores de desempeño de las operaciones (KPIs) y reportes exportables
 *              (CSV o JSON) a partir del historial de simulaciones.
 */
@Service
public class DesempenoService {

    private final HistorialSimulacionRepository historialRepository;

    public DesempenoService(HistorialSimulacionRepository historialRepository) {
        this.historialRepository = historialRepository;
    }

    /** KPI de la corrida más reciente de un algoritmo. */
    public KpiResponseDTO kpis(String algoritmo) {
        List<HistorialSimulacion> corridas = historialRepository.porAlgoritmo(algoritmo);
        if (corridas.isEmpty()) return null;
        return kpiDesde(corridas.get(corridas.size() - 1));
    }

    /** Comparativa ACS vs GRASP-VNS con la corrida más reciente de cada uno. */
    public List<KpiResponseDTO> comparativa() {
        List<KpiResponseDTO> resultado = new ArrayList<>();
        KpiResponseDTO acs = kpis("ACS");
        KpiResponseDTO grasp = kpis("GRASP_VNS");
        if (acs != null) resultado.add(acs);
        if (grasp != null) resultado.add(grasp);
        return resultado;
    }

    /** Reporte JSON: un KPI por corrida del historial. */
    public List<KpiResponseDTO> reporteJson() {
        List<KpiResponseDTO> resultado = new ArrayList<>();
        for (HistorialSimulacion h : historialRepository.listar()) {
            resultado.add(kpiDesde(h));
        }
        return resultado;
    }

    /** Reporte CSV: una fila por corrida del historial. */
    public String reporteCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("tipo,algoritmo,semilla,lambda,colapso,momento_colapso,pedidos_ingresados,"
                + "pedidos_completados,cumplimiento_pct,entregas_totales,entregas_parciales,"
                + "costo_total_soles,kilometros_recorridos,costo_por_km,costo_por_pedido\n");
        for (HistorialSimulacion h : historialRepository.listar()) {
            sb.append(String.format(Locale.US,
                    "%s,%s,%d,%.4f,%s,%s,%d,%d,%.4f,%d,%d,%.2f,%.2f,%.4f,%.4f%n",
                    h.getTipo(), h.getAlgoritmo(), h.getSemilla(), h.getLambda(), h.isColapso(),
                    h.getMomentoColapso() != null ? h.getMomentoColapso() : "",
                    h.getPedidosIngresados(), h.getPedidosCompletadosATiempo(),
                    porcentaje(h.getPedidosCompletadosATiempo(), h.getPedidosIngresados()),
                    h.getEntregasTotales(), h.getEntregasParciales(),
                    h.getCostoAcumuladoSoles(), h.getKilometrosRecorridos(),
                    costoPorKm(h.getCostoAcumuladoSoles(), h.getKilometrosRecorridos()),
                    costoPorPedido(h.getCostoAcumuladoSoles(), h.getPedidosCompletadosATiempo())));
        }
        return sb.toString();
    }

    public KpiResponseDTO kpiDesde(HistorialSimulacion h) {
        double pctCumplimiento = porcentaje(h.getPedidosCompletadosATiempo(), h.getPedidosIngresados());
        double pctParciales = porcentaje(h.getEntregasParciales(), h.getEntregasTotales());
        return KpiResponseDTO.builder()
                .algoritmo(h.getAlgoritmo())
                .pedidosIngresados(h.getPedidosIngresados())
                .pedidosCompletados(h.getPedidosCompletadosATiempo())
                .porcentajeCumplimiento(pctCumplimiento)
                .entregasTotales(h.getEntregasTotales())
                .entregasParciales(h.getEntregasParciales())
                .porcentajeEntregasParciales(pctParciales)
                .costoTotalSoles(h.getCostoAcumuladoSoles())
                .kilometrosRecorridos(h.getKilometrosRecorridos())
                .costoPorKm(costoPorKm(h.getCostoAcumuladoSoles(), h.getKilometrosRecorridos()))
                .costoPorPedido(costoPorPedido(h.getCostoAcumuladoSoles(), h.getPedidosCompletadosATiempo()))
                .build();
    }

    private double porcentaje(int parte, int todo) {
        return todo == 0 ? 0.0 : parte * 100.0 / todo;
    }

    private double costoPorKm(double costo, double km) {
        return km == 0 ? 0.0 : costo / km;
    }

    private double costoPorPedido(double costo, int pedidos) {
        return pedidos == 0 ? 0.0 : costo / pedidos;
    }
}