package paqroute.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRun(@RequestBody Map<String, Object> config) {
        // TODO: Inicializar la simulación con el config dado
        String runId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "runId", runId,
            "controllerToken", token,
            "status", "running",
            "transportVersion", "1"
        ));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRuns() {
        // TODO: Retornar los runs activos
        return ResponseEntity.ok(Collections.emptyList());
    }

    @GetMapping("/{runId}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable String runId) {
        // TODO: Retornar el snapshot del run
        return ResponseEntity.ok(Map.of(
            "transportVersion", "1",
            "runId", runId,
            "status", "running",
            "finished", null,
            "lastSeq", 0,
            "lastPlan", null,
            "config", Map.of(),
            "sentAt", java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/{runId}/commands")
    public ResponseEntity<Map<String, Object>> executeCommand(
            @PathVariable String runId,
            @RequestHeader(value = "X-Controller-Token", required = false) String token,
            @RequestBody Map<String, Object> payload) {
            
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "not_controller",
                "message", "Falta el token controlador",
                "details", Map.of()
            ));
        }

        // TODO: Validar comando y token. Encolar comando para la simulacion.
        String commandId = (String) payload.getOrDefault("commandId", UUID.randomUUID().toString());
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "accepted", true,
            "commandId", commandId
        ));
    }

    @PostMapping("/{runId}/claim")
    public ResponseEntity<Map<String, Object>> claimRun(@PathVariable String runId) {
        // TODO: Rotar el token del run
        String newToken = UUID.randomUUID().toString();
        
        return ResponseEntity.ok(Map.of(
            "runId", runId,
            "controllerToken", newToken
        ));
    }
}
