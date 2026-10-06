package com.paqroute.backend.controller;

import com.paqroute.backend.dto.request.PedidoRegistroRequestDTO;
import com.paqroute.backend.dto.response.ClienteResponseDTO;
import com.paqroute.backend.dto.response.PedidoResponseDTO;
import com.paqroute.backend.mapper.PedidoMapper;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST del registro de pedidos (alta individual y consulta).
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> registrar(@Valid @RequestBody PedidoRegistroRequestDTO requestDTO) {
        final Pedido pedido = pedidoService.registrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoMapper.toResponseDTO(pedido));
    }

    @GetMapping
    public List<PedidoResponseDTO> listar(@RequestParam(defaultValue = "100") int limite) {
        return pedidoService.listar(limite).stream()
                .map(PedidoMapper::toResponseDTO)
                .toList();
    }

    @GetMapping("/clientes")
    public List<ClienteResponseDTO> clientes(@RequestParam(defaultValue = "200") int limite) {
        return pedidoService.clientes(limite);
    }
}