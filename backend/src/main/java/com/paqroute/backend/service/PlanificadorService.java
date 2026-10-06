package com.paqroute.backend.service;

import com.paqroute.backend.algorithm.IEstrategiaEnrutamiento;
import com.paqroute.backend.algorithm.PlanificadorRutas;
import com.paqroute.backend.dto.request.PlanificarRutasRequestDTO;
import com.paqroute.backend.dto.response.RutasPlanificadasResponseDTO;
import com.paqroute.backend.engine.AntColonySystemVRP;
import com.paqroute.backend.engine.GrafoVial;
import com.paqroute.backend.engine.GraspVnsVRP;
import com.paqroute.backend.engine.Simulador;
import com.paqroute.backend.exception.BusinessException;
import com.paqroute.backend.mapper.RutaMapper;
import com.paqroute.backend.model.Almacen;
import com.paqroute.backend.model.Bloqueo;
import com.paqroute.backend.model.ContextoPlanificacion;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Ruta;
import com.paqroute.backend.model.UnidadTransporte;
import com.paqroute.backend.repository.BloqueoRepository;
import com.paqroute.backend.repository.PedidoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Planificación "one-shot" de rutas: dado un instante, arma el contexto del tick y
 *              delega en el PatronRutas con la estrategia elegida (ACS o GRASP-VNS).
 */
@Service
public class PlanificadorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanificadorService.class);

    private final PedidoRepository pedidoRepository;
    private final BloqueoRepository bloqueoRepository;
    private final int maxPedidos;

    public PlanificadorService(PedidoRepository pedidoRepository,
                               BloqueoRepository bloqueoRepository,
                               @Value("${paqroute.planificador.max-pedidos:300}") int maxPedidos) {
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.maxPedidos = maxPedidos;
    }

    public RutasPlanificadasResponseDTO planificar(PlanificarRutasRequestDTO request) {
        final Simulador.Algoritmo algoritmo = parsearAlgoritmo(request.getAlgoritmo());
        final LocalDateTime hora = calcularHora(request.getHora());

        final List<Pedido> pendientes = new ArrayList<>();
        for (Pedido p : pedidoRepository.listarTodos()) {
            if (p.momentoLlegada.isAfter(hora)) continue;
            if (p.completo()) continue;
            pendientes.add(p);
            if (pendientes.size() >= maxPedidos) break;
        }
        if (pendientes.isEmpty()) {
            throw new BusinessException("No hay pedidos pendientes hasta " + hora);
        }

        final List<Almacen> almacenes = Simulador.crearAlmacenes();
        final List<UnidadTransporte> flota = Simulador.construirFlota(almacenes.get(0).ubicacion, hora);

        final GrafoVial grafo = new GrafoVial();
        grafo.actualizarBloqueosVigentes(Bloqueo.aristasVigentesEn(bloqueoRepository.listarTodos(), hora));
        final ContextoPlanificacion ctx = new ContextoPlanificacion(almacenes, grafo, hora);

        final Simulador.ParametrosAlgoritmo parametros = Boolean.TRUE.equals(request.getAcsMejorado())
                ? Simulador.ParametrosAlgoritmo.DEFAULT
                        .conMemoriaAcs(0.3).conBusquedaLocalAcs(true).conTipoVehiculoAmpliadoAcs(true)
                : Simulador.ParametrosAlgoritmo.DEFAULT;

        final IEstrategiaEnrutamiento estrategia = switch (algoritmo) {
            case ACS -> new AntColonySystemVRP(pendientes, flota, ctx, 1L,
                    parametros.numHormigas(), parametros.numIteracionesAcs(), parametros.alfaAcs(),
                    parametros.betaAcs(), parametros.rho(), parametros.q0(),
                    null, 0.0, parametros.busquedaLocalAcs(), parametros.tipoVehiculoAmpliadoAcs());
            case GRASP_VNS -> new GraspVnsVRP(pendientes, flota, ctx, 1L,
                    parametros.alfaGrasp(), parametros.maxIteracionesGrasp());
        };

        final PlanificadorRutas planificador = new PlanificadorRutas();
        planificador.setEstrategia(estrategia);
        final List<Ruta> rutas = planificador.ejecutarPlanificacion(pendientes, flota);

        LOGGER.info("Planificación one-shot en {} con {}: {} rutas, {} entregas",
                hora, algoritmo, rutas.size(), rutas.stream().mapToInt(Ruta::cargaTotal).sum());
        return RutasPlanificadasResponseDTO.builder()
                .totalRutas(rutas.size())
                .totalEntregas(rutas.size())
                .rutas(RutaMapper.listar(rutas))
                .build();
    }

    private LocalDateTime calcularHora(LocalDateTime hora) {
        if (hora != null) return hora;
        List<Pedido> base = pedidoRepository.listarTodos();
        return base.isEmpty() ? LocalDateTime.now() : base.get(0).momentoLlegada;
    }

    private Simulador.Algoritmo parsearAlgoritmo(String valor) {
        try {
            return Simulador.Algoritmo.valueOf(valor != null ? valor.trim().toUpperCase() : "");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Algoritmo inválido: " + valor + " (esperado ACS o GRASP_VNS)");
        }
    }
}