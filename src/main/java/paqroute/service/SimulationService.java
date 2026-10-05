package paqroute.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import paqroute.dto.EnvelopeDto;
import paqroute.dto.RunEventDto;
import paqroute.Simulador;
// ... imports adicionales ...
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Service
public class SimulationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    // Almacenamos el estado de cada run por su runId
    private final Map<String, RunContext> runs = new ConcurrentHashMap<>();

    public SimulationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void startSimulation(String runId, Map<String, Object> config) {
        RunContext ctx = new RunContext(runId);
        runs.put(runId, ctx);
        
        executor.submit(() -> runSimulationLoop(ctx));
    }

    private void runSimulationLoop(RunContext ctx) {
        // Aca estaria la logica del Simulador adaptada
        // Por ahora es un dummy que emite un plan cada segundo
        
        try {
            while (!ctx.isTerminated) {
                if (ctx.isPaused) {
                    Thread.sleep(100);
                    continue;
                }
                
                // Simular el trabajo de un tick (planificarTick)
                Thread.sleep(1000); 
                
                ctx.seq++;
                
                EnvelopeDto envelope = new EnvelopeDto();
                envelope.setTransportVersion("1");
                envelope.setRunId(ctx.runId);
                envelope.setSeq(ctx.seq);
                envelope.setSentAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                envelope.setKind("plan");
                envelope.setPayloadVersion("plan-iteration-1.0.0");
                
                // TODO: armar el DTO real del plan a partir de Solucion
                envelope.setPayload(Map.of("message", "Dummy plan")); 
                
                messagingTemplate.convertAndSend("/topic/runs/" + ctx.runId + "/stream", envelope);
                
                // TODO: manejar commands encolados (pause, resume, parameters) y emitir "run-event"
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void handleCommand(String runId, String commandId, String command, Map<String, Object> payload) {
        RunContext ctx = runs.get(runId);
        if (ctx == null) return;
        
        // TODO: Encolar comandos para que el loop de simulacion los procese en el proximo boundary
    }

    private static class RunContext {
        String runId;
        int seq = 0;
        volatile boolean isPaused = false;
        volatile boolean isTerminated = false;
        
        public RunContext(String runId) {
            this.runId = runId;
        }
    }
}
