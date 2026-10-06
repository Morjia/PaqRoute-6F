package com.paqroute.backend.util;

import com.paqroute.backend.dto.request.EscenarioRequestDTO;
import com.paqroute.backend.dto.response.EscenarioResponseDTO;
import com.paqroute.backend.model.HistorialSimulacion;
import com.paqroute.backend.service.EscenarioService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Motor de simulación (diseño de clases). Controla los modos de ejecución
 *              (día a día, 5 días y colapso) y emite alertas de saturación, delegando la
 *              ejecución en el servicio de escenarios.
 */
@Service
public class MotorSimulacion {

    private final EscenarioService escenarioService;

    public MotorSimulacion(EscenarioService escenarioService) {
        this.escenarioService = escenarioService;
    }

    public List<String> modos() {
        return List.of("DIA_A_DIA", "CINCO_DIAS", "COLAPSO");
    }

    public EscenarioResponseDTO iniciarSimulacionDiaADia(EscenarioRequestDTO base) {
        return ejecutarModo(base, "OPERACION_DIARIA");
    }

    public EscenarioResponseDTO iniciarSimulacion5D(EscenarioRequestDTO base) {
        return ejecutarModo(base, "CINCO_DIAS");
    }

    public EscenarioResponseDTO iniciarSimulacionColapso(EscenarioRequestDTO base) {
        return ejecutarModo(base, "COLAPSO");
    }

    /**
     * Alerta de saturación basada en la última corrida: colapso o consumo de SLA superior al 90%.
     */
    public Map<String, Object> emitirAlertaSaturacion() {
        List<HistorialSimulacion> historial = escenarioService.historial();
        if (historial.isEmpty()) {
            return Map.of("alerta", false, "mensaje", "Sin corridas registradas");
        }
        HistorialSimulacion ultimo = historial.get(historial.size() - 1);
        boolean colapso = ultimo.isColapso();
        boolean slaCritico = ultimo.getConsumoSlaPromedioPct() != null
                && ultimo.getConsumoSlaPromedioPct() > 90.0;
        return Map.of(
                "alerta", colapso || slaCritico,
                "tipo", ultimo.getTipo(),
                "algoritmo", ultimo.getAlgoritmo(),
                "colapso", colapso,
                "consumoSlaPromedioPct", ultimo.getConsumoSlaPromedioPct() != null
                        ? ultimo.getConsumoSlaPromedioPct() : 0.0);
    }

    private EscenarioResponseDTO ejecutarModo(EscenarioRequestDTO base, String tipo) {
        EscenarioRequestDTO request = new EscenarioRequestDTO();
        request.setTipo(tipo);
        request.setAlgoritmo(base.getAlgoritmo());
        request.setDesde(base.getDesde());
        request.setDuracionHoras(base.getDuracionHoras());
        request.setLambda(base.getLambda());
        request.setReplicas(base.getReplicas());
        request.setAcsMejorado(base.getAcsMejorado());
        return escenarioService.ejecutarSincrono(request);
    }
}