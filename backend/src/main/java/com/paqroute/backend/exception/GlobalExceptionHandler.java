package com.paqroute.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Manejador global de excepciones. Estandariza las respuestas de error
 *              con un ApiErrorResponse, sin exponer stacktraces ni rutas internas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> manejarNoEncontrado(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Error controlado recurso no encontrado ruta={}, mensaje={}",
                request.getRequestURI(), exception.getMessage());
        return construirRespuesta(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> manejarReglaNegocio(
            BusinessException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Error controlado de negocio ruta={}, mensaje={}",
                request.getRequestURI(), exception.getMessage());
        return construirRespuesta(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ArchivoInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> manejarArchivoInvalido(
            ArchivoInvalidoException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Error controlado de archivo ruta={}, mensaje={}",
                request.getRequestURI(), exception.getMessage());
        return construirRespuesta(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> manejarValidacion(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        final String mensaje = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Datos de entrada inválidos");
        LOGGER.warn("Error controlado de validacion ruta={}, mensaje={}", request.getRequestURI(), mensaje);
        return construirRespuesta(HttpStatus.BAD_REQUEST, mensaje, request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> manejarContentType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Content-Type no soportado ruta={}", request.getRequestURI());
        return construirRespuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type no soportado", request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> manejarBodyInvalido(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Cuerpo de solicitud invalido ruta={}", request.getRequestURI());
        return construirRespuesta(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud invalido o ausente", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> manejarTipoParametro(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Parametro con tipo incorrecto ruta={}", request.getRequestURI());
        return construirRespuesta(HttpStatus.BAD_REQUEST, "Parametro con formato invalido", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> manejarErrorGeneral(Exception exception, HttpServletRequest request) {
        LOGGER.error("Error no controlado ruta={}", request.getRequestURI(), exception);
        return construirRespuesta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrio un error interno en el servidor.",
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiErrorResponse> construirRespuesta(HttpStatus estado, String mensaje, String ruta) {
        final ApiErrorResponse respuesta = ApiErrorResponse.builder()
                .fechaHora(LocalDateTime.now())
                .estado(estado.value())
                .error(estado.getReasonPhrase())
                .mensaje(mensaje)
                .ruta(ruta)
                .build();
        return ResponseEntity.status(estado).body(respuesta);
    }
}