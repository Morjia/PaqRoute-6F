package com.paqroute.backend.service;

import com.paqroute.backend.dto.request.IncidenciaRequestDTO;
import com.paqroute.backend.enums.TipoIncidencia;
import com.paqroute.backend.exception.BusinessException;
import com.paqroute.backend.model.HistorialReplanificacion;
import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.model.Punto;
import com.paqroute.backend.repository.IncidenciaRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Servicio de registro de incidencias (bloqueos de calle y fallas vehiculares).
 *              Las incidencias se aplican en las corridas de simulación y quedan en un historial
 *              de replanificación.
 */
@Service
public class IncidenciaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidenciaService.class);

    private final IncidenciaRepository incidenciaRepository;
    private final NotificacionService notificacionService;

    public IncidenciaService(IncidenciaRepository incidenciaRepository, NotificacionService notificacionService) {
        this.incidenciaRepository = incidenciaRepository;
        this.notificacionService = notificacionService;
    }

    public Incidencia registrar(IncidenciaRequestDTO request) {
        final TipoIncidencia tipo = parsearTipo(request.getTipo());
        if (tipo == TipoIncidencia.BLOQUEO_CALLE) {
            if (request.getPoligonal() == null || request.getPoligonal().size() < 2) {
                throw new BusinessException("Un bloqueo de calle requiere una polilínea con al menos 2 puntos [x,y]");
            }
        }
        if (tipo == TipoIncidencia.FALLA_VEHICULAR && (request.getVehiculoId() == null || request.getVehiculoId().isBlank())) {
            throw new BusinessException("Una falla vehicular requiere el identificador del vehículo (TTNN)");
        }

        final Incidencia incidencia = new Incidencia();
        incidencia.setTipo(tipo);
        incidencia.setMotivo(request.getMotivo());
        incidencia.setDuracionEstimadaHoras(request.getDuracionEstimadaHoras() != null ? request.getDuracionEstimadaHoras() : 1.0);
        incidencia.setDesde(request.getDesde() != null ? request.getDesde() : LocalDateTime.now());
        incidencia.setVehiculoId(request.getVehiculoId());
        incidencia.setPoligonal(convertirPoligonal(request));
        incidencia.setResuelta(false);
        incidencia.setFechaRegistro(LocalDateTime.now());

        final Incidencia guardada = incidenciaRepository.guardar(incidencia);

        final HistorialReplanificacion registro = new HistorialReplanificacion();
        registro.setIncidenciaId(guardada.getId());
        registro.setTipo(tipo.name());
        registro.setDetalle("Incidencia " + tipo + " registrada y aplicada a las simulaciones: "
                + (request.getMotivo() != null ? request.getMotivo() : "sin motivo especificado"));
        registro.setRutasEvaluadas(0);
        registro.setFecha(LocalDateTime.now());
        incidenciaRepository.registrarReplanificacion(registro);

        LOGGER.info("Incidencia registrada id={} tipo={} vehiculoId={} desde={}",
                guardada.getId(), tipo, request.getVehiculoId(), guardada.getDesde());
        notificacionService.registrarEvento("Incidencia " + tipo + " registrada (id=" + guardada.getId() + ")");
        return guardada;
    }

    public List<Incidencia> listar() {
        return incidenciaRepository.listarIncidencias();
    }

    public List<HistorialReplanificacion> historial() {
        return incidenciaRepository.listarHistorial();
    }

    private List<Punto> convertirPoligonal(IncidenciaRequestDTO request) {
        if (request.getPoligonal() == null) return null;
        List<Punto> puntos = new ArrayList<>();
        for (List<Integer> par : request.getPoligonal()) {
            if (par == null || par.size() < 2) {
                throw new BusinessException("Cada punto de la polilínea debe ser [x,y]");
            }
            int x = par.get(0);
            int y = par.get(1);
            if (x < 0 || x > 70 || y < 0 || y > 50) {
                throw new BusinessException("Punto fuera de la cuadrícula (0..70, 0..50): [" + x + "," + y + "]");
            }
            puntos.add(new Punto(x, y));
        }
        return puntos;
    }

    private TipoIncidencia parsearTipo(String valor) {
        try {
            return TipoIncidencia.valueOf(valor != null ? valor.trim().toUpperCase() : "");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Tipo de incidencia inválido: " + valor
                    + " (esperado BLOQUEO_CALLE o FALLA_VEHICULAR)");
        }
    }
}