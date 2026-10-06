package com.paqroute.backend.service;

import com.paqroute.backend.dto.request.EscenarioRequestDTO;
import com.paqroute.backend.dto.response.EscenarioResponseDTO;
import com.paqroute.backend.dto.response.RutaResponseDTO;
import com.paqroute.backend.dto.response.TickSnapshotDTO;
import com.paqroute.backend.dto.response.VehiculoEstadoDTO;
import com.paqroute.backend.enums.EscenarioTipo;
import com.paqroute.backend.engine.ResultadoSimulacion;
import com.paqroute.backend.engine.Simulador;
import com.paqroute.backend.exception.BusinessException;
import com.paqroute.backend.mapper.ResultadoSimulacionMapper;
import com.paqroute.backend.mapper.RutaMapper;
import com.paqroute.backend.model.Bloqueo;
import com.paqroute.backend.model.HistorialSimulacion;
import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.model.Mantenimiento;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.UnidadTransporte;
import com.paqroute.backend.repository.BloqueoRepository;
import com.paqroute.backend.repository.HistorialSimulacionRepository;
import com.paqroute.backend.repository.IncidenciaRepository;
import com.paqroute.backend.repository.MantenimientoRepository;
import com.paqroute.backend.repository.PedidoRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Servicio de configuración y ejecución de escenarios (operación diaria, 5 días y
 *              colapso). Integra las incidencias registradas, ejecuta las réplicas con el
 *              simulador, publica el avance por WebSocket (STOMP) y guarda el historial.
 */
@Service
public class EscenarioService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EscenarioService.class);
    private static final int TICK_MINUTOS = 30;
    private static final int MAX_REPLICAS = 20;
    private static final long DURACION_DIARIA_DEFECTO = 24;
    private static final long DURACION_CINCO_DIAS_DEFECTO = 120;

    private final PedidoRepository pedidoRepository;
    private final BloqueoRepository bloqueoRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final HistorialSimulacionRepository historialRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor();
    private final Map<String, ControlState> controles = new ConcurrentHashMap<>();

    public EscenarioService(PedidoRepository pedidoRepository,
                            BloqueoRepository bloqueoRepository,
                            MantenimientoRepository mantenimientoRepository,
                            IncidenciaRepository incidenciaRepository,
                            HistorialSimulacionRepository historialRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.historialRepository = historialRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @PreDestroy
    public void detener() {
        ejecutor.shutdownNow();
    }

    /** Estado compartido de control de una simulación en streaming (pausa y velocidad). */
    public static final class ControlState {
        public volatile boolean pausado = false;
        public volatile long retrasoMs = 0L;

        public ControlState() {
        }

        public ControlState(long retrasoMs) {
            this.retrasoMs = retrasoMs;
        }
    }

    public List<String> tipos() {
        List<String> lista = new ArrayList<>();
        for (EscenarioTipo t : EscenarioTipo.values()) lista.add(t.name());
        return lista;
    }

    public List<String> algoritmos() {
        return List.of(Simulador.Algoritmo.ACS.name(), Simulador.Algoritmo.GRASP_VNS.name());
    }

    public void controlar(String id, String comando, Long valor) {
        ControlState estado = controles.computeIfAbsent(id, k -> new ControlState());
        switch (comando == null ? "" : comando.toUpperCase()) {
            case "PAUSAR" -> estado.pausado = true;
            case "REANUDAR" -> estado.pausado = false;
            case "VELOCIDAD" -> estado.retrasoMs = valor != null ? valor : 0L;
            default -> throw new BusinessException("Comando de control inválido: " + comando
                    + " (esperado PAUSAR, REANUDAR o VELOCIDAD)");
        }
    }

    /** Ejecuta el escenario de forma síncrona (todas las réplicas) y devuelve los resultados. */
    public EscenarioResponseDTO ejecutarSincrono(EscenarioRequestDTO request) {
        Preparacion prep = preparar(request);
        List<com.paqroute.backend.dto.response.ResultadoSimulacionResponseDTO> resultados = new ArrayList<>();
        Integer primerHistorial = null;
        for (long semilla = 1; semilla <= prep.replicas; semilla++) {
            ResultadoSimulacion r = correr(prep, semilla, null);
            HistorialSimulacion guardado = historialRepository.guardar(
                    HistorialSimulacion.desdeResultado(null, prep.tipo.name(), prep.algoritmo.name(),
                            semilla, prep.lambda, prep.desde, r));
            if (primerHistorial == null) primerHistorial = guardado.getId();
            resultados.add(ResultadoSimulacionMapper.toResponseDTO(r));
        }
        return EscenarioResponseDTO.builder()
                .idHistorial(primerHistorial)
                .tipo(prep.tipo.name())
                .algoritmo(prep.algoritmo.name())
                .replicas(prep.replicas)
                .estado("EJECUTADO")
                .resultados(resultados)
                .build();
    }

    /** Inicia el escenario en segundo plano publicando ticks por WebSocket; devuelve el id del stream. */
    public String iniciarStreaming(EscenarioRequestDTO request) {
        Preparacion prep = preparar(request);
        String id = UUID.randomUUID().toString();
        long pausaInicial = request.getPausaMs() != null ? request.getPausaMs() : 0L;
        controles.put(id, new ControlState(pausaInicial));

        ejecutor.submit(() -> {
            try {
                for (long semilla = 1; semilla <= prep.replicas; semilla++) {
                    Simulador.TickListener listener = evento -> {
                        ControlState estado = controles.get(id);
                        while (estado != null && estado.pausado) {
                            dormir(50);
                        }
                        messagingTemplate.convertAndSend("/topic/simulacion/" + id,
                                construirSnapshot(id, evento));
                        dormir(estado != null ? estado.retrasoMs : 0L);
                    };
                    ResultadoSimulacion r = correr(prep, semilla, listener);
                    HistorialSimulacion h = historialRepository.guardar(
                            HistorialSimulacion.desdeResultado(null, prep.tipo.name(), prep.algoritmo.name(),
                                    semilla, prep.lambda, prep.desde, r));
                    messagingTemplate.convertAndSend("/topic/simulacion/" + id + "/resultado",
                            ResultadoSimulacionMapper.toResponseDTO(r));
                    LOGGER.info("Streaming de simulación {} terminó (réplica {}), historial={}", id, semilla, h.getId());
                }
            } catch (Exception e) {
                LOGGER.error("Error en streaming de simulación {}", id, e);
                messagingTemplate.convertAndSend("/topic/simulacion/" + id + "/error", e.getMessage());
            } finally {
                controles.remove(id);
            }
        });
        return id;
    }

    public Map<String, ControlState> controlesActivos() {
        return new ConcurrentHashMap<>(controles);
    }

    public List<HistorialSimulacion> historial() {
        return historialRepository.listar();
    }

    // ------------------------------------------------------------- preparación

    private record Preparacion(EscenarioTipo tipo, Simulador.Algoritmo algoritmo,
                               Simulador.ParametrosAlgoritmo parametros,
                               List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                               List<Pedido> pedidos, double lambda, LocalDateTime desde, int replicas) {
    }

    private Preparacion preparar(EscenarioRequestDTO request) {
        final EscenarioTipo tipo = parsearTipo(request.getTipo());
        final Simulador.Algoritmo algoritmo = parsearAlgoritmo(request.getAlgoritmo());
        final double lambda = request.getLambda() != null ? request.getLambda() : 1.0;
        final int replicas = request.getReplicas() != null ? request.getReplicas() : 1;
        if (replicas < 1 || replicas > MAX_REPLICAS) {
            throw new BusinessException("El número de réplicas debe estar entre 1 y " + MAX_REPLICAS);
        }
        if (lambda < 0.25 || lambda > 128.0) {
            throw new BusinessException("El factor de carga lambda debe estar entre 0.25 y 128");
        }

        final List<Pedido> base = pedidoRepository.listarTodos();
        final LocalDateTime desde = request.getDesde() != null
                ? request.getDesde()
                : (base.isEmpty() ? LocalDateTime.now() : base.get(0).momentoLlegada);

        final LocalDateTime hasta;
        if (tipo == EscenarioTipo.OPERACION_DIARIA) {
            long d = request.getDuracionHoras() != null ? request.getDuracionHoras().longValue() : DURACION_DIARIA_DEFECTO;
            hasta = desde.plusHours(d);
        } else if (tipo == EscenarioTipo.CINCO_DIAS) {
            long d = request.getDuracionHoras() != null ? request.getDuracionHoras().longValue() : DURACION_CINCO_DIAS_DEFECTO;
            hasta = desde.plusHours(d);
        } else {
            hasta = null; // COLAPSO: todo el horizonte disponible
        }

        final List<Pedido> ventana = new ArrayList<>();
        for (Pedido p : base) {
            if (p.momentoLlegada.isBefore(desde)) continue;
            if (hasta != null && !p.momentoLlegada.isBefore(hasta)) continue;
            ventana.add(p);
        }

        final Simulador.ParametrosAlgoritmo parametros = Boolean.TRUE.equals(request.getAcsMejorado())
                ? Simulador.ParametrosAlgoritmo.DEFAULT
                        .conMemoriaAcs(0.3).conBusquedaLocalAcs(true).conTipoVehiculoAmpliadoAcs(true)
                : Simulador.ParametrosAlgoritmo.DEFAULT;

        return new Preparacion(tipo, algoritmo, parametros,
                construirBloqueos(), construirMantenimientos(), ventana, lambda, desde, replicas);
    }

    private List<Bloqueo> construirBloqueos() {
        List<Bloqueo> resultado = new ArrayList<>(bloqueoRepository.listarTodos());
        for (Incidencia i : incidenciaRepository.listarIncidencias()) {
            if (i.getTipo() != com.paqroute.backend.enums.TipoIncidencia.BLOQUEO_CALLE || i.isResuelta()) continue;
            if (i.getPoligonal() == null || i.getPoligonal().size() < 2) continue;
            LocalDateTime ini = i.getDesde() != null ? i.getDesde() : LocalDateTime.now();
            double dur = i.getDuracionEstimadaHoras() > 0 ? i.getDuracionEstimadaHoras() : 1.0;
            resultado.add(new Bloqueo(ini, ini.plusHours((long) Math.ceil(dur)), i.getPoligonal()));
        }
        return resultado;
    }

    private List<Mantenimiento> construirMantenimientos() {
        List<Mantenimiento> resultado = new ArrayList<>(mantenimientoRepository.listarTodos());
        for (Incidencia i : incidenciaRepository.listarIncidencias()) {
            if (i.getTipo() != com.paqroute.backend.enums.TipoIncidencia.FALLA_VEHICULAR || i.isResuelta()) continue;
            if (i.getVehiculoId() == null || i.getVehiculoId().isBlank()) continue;
            LocalDateTime ini = i.getDesde() != null ? i.getDesde() : LocalDateTime.now();
            try {
                resultado.add(new Mantenimiento(ini.toLocalDate(), i.getVehiculoId()));
            } catch (RuntimeException ignorable) {
                LOGGER.warn("Falla vehicular con id inválido ignorada: {}", i.getVehiculoId());
            }
        }
        return resultado;
    }

    private ResultadoSimulacion correr(Preparacion prep, long semilla, Simulador.TickListener listener) {
        List<Pedido> copia = new ArrayList<>(prep.pedidos.size());
        for (Pedido p : prep.pedidos) copia.add(p.copiaEscalada(prep.lambda));
        Simulador simulador = new Simulador(copia, prep.bloqueos, prep.mantenimientos,
                Duration.ofMinutes(TICK_MINUTOS), prep.algoritmo, semilla, prep.desde, prep.parametros);
        return listener == null ? simulador.ejecutar() : simulador.ejecutar(listener, 0L);
    }

    private TickSnapshotDTO construirSnapshot(String id, Simulador.TickEvent evento) {
        List<VehiculoEstadoDTO> vehiculos = new ArrayList<>();
        for (UnidadTransporte v : evento.flota()) {
            vehiculos.add(VehiculoEstadoDTO.builder()
                    .id(v.idUnidad)
                    .tipo(v.tipo.name())
                    .estado(v.estado.name())
                    .x(v.posicionActual.x)
                    .y(v.posicionActual.y)
                    .disponibleDesde(v.disponibleDesde != null ? v.disponibleDesde.toString() : null)
                    .build());
        }
        List<RutaResponseDTO> rutas = RutaMapper.listar(evento.rutasPlanificadas());
        return TickSnapshotDTO.builder()
                .simulacionId(id)
                .horaActual(evento.horaActual())
                .colapso(false)
                .pedidosPendientes(evento.pendientes().size())
                .vehiculos(vehiculos)
                .rutas(rutas)
                .build();
    }

    private EscenarioTipo parsearTipo(String valor) {
        try {
            return EscenarioTipo.valueOf(valor != null ? valor.trim().toUpperCase() : "");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Tipo de escenario inválido: " + valor
                    + " (esperado OPERACION_DIARIA, CINCO_DIAS o COLAPSO)");
        }
    }

    private Simulador.Algoritmo parsearAlgoritmo(String valor) {
        try {
            return Simulador.Algoritmo.valueOf(valor != null ? valor.trim().toUpperCase() : "");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Algoritmo inválido: " + valor + " (esperado ACS o GRASP_VNS)");
        }
    }

    private void dormir(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}