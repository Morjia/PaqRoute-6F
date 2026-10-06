package com.paqroute.backend.service;

import com.paqroute.backend.dto.request.PedidoRegistroRequestDTO;
import com.paqroute.backend.dto.response.ClienteResponseDTO;
import com.paqroute.backend.exception.BusinessException;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Punto;
import com.paqroute.backend.repository.PedidoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Registro de pedidos: alta individual por API y consulta de los pedidos/clientes
 *              cargados desde ventas.
 */
@Service
public class PedidoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final NotificacionService notificacionService;
    private final AtomicInteger secuenciaRegistro = new AtomicInteger(0);

    public PedidoService(PedidoRepository pedidoRepository, NotificacionService notificacionService) {
        this.pedidoRepository = pedidoRepository;
        this.notificacionService = notificacionService;
    }

    public List<Pedido> listar(int limite) {
        List<Pedido> todos = pedidoRepository.listarTodos();
        int max = Math.min(limite, todos.size());
        return new ArrayList<>(todos.subList(0, max));
    }

    public List<ClienteResponseDTO> clientes(int limite) {
        List<ClienteResponseDTO> resultado = new ArrayList<>();
        for (Map.Entry<String, Long> e : pedidoRepository.clientes().entrySet()) {
            if (resultado.size() >= limite) break;
            resultado.add(ClienteResponseDTO.builder().idCliente(e.getKey()).totalPedidos(e.getValue()).build());
        }
        return resultado;
    }

    public Pedido registrar(PedidoRegistroRequestDTO request) {
        if (request.getCantidad() == null || request.getCantidad() < 1) {
            throw new BusinessException("La cantidad debe ser al menos 1");
        }
        if (request.getHorasLimite() == null || request.getHorasLimite() <= 0) {
            throw new BusinessException("El plazo (horas límite) debe ser positivo");
        }
        final String idCliente = request.getIdCliente() == null || request.getIdCliente().isBlank()
                ? "cind" + secuenciaRegistro.incrementAndGet()
                : request.getIdCliente().trim();
        final LocalDateTime ahora = LocalDateTime.now();
        final Pedido pedido = new Pedido(idCliente, new Punto(request.getX(), request.getY()),
                request.getCantidad(), ahora, request.getHorasLimite());
        pedidoRepository.registrar(pedido);
        LOGGER.info("Pedido registrado por API idCliente={} cantidad={} plazo={}h en ({},{})",
                idCliente, request.getCantidad(), request.getHorasLimite(), request.getX(), request.getY());
        notificacionService.registrarEvento("Pedido registrado por API: " + idCliente);
        return pedido;
    }
}