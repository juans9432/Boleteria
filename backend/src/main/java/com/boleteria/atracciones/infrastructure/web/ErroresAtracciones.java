package com.boleteria.atracciones.infrastructure.web;

import com.boleteria.atracciones.application.port.in.GestionarAtracciones.Denegado;
import com.boleteria.atracciones.application.port.out.Atracciones.Fallo;
import com.boleteria.atracciones.domain.DatosAtraccion;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AtraccionesController.class)
class ErroresAtracciones {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de atracciones.");
    }

    @ExceptionHandler(DatosAtraccion.Invalido.class)
    ProblemDetail invalido(DatosAtraccion.Invalido error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Fallo.class)
    ProblemDetail fallo(Fallo error) {
        return switch (error.motivo()) {
            
            case REFERENCIADO -> problema(HttpStatus.CONFLICT, "La atracción tiene funciones o combos asociados y no se puede eliminar.");
            case PRESTADOR_INEXISTENTE -> problema(HttpStatus.BAD_REQUEST, "El prestador seleccionado no existe.");
            case CAMPO_EXTENSO -> problema(HttpStatus.BAD_REQUEST, "Un campo supera el espacio disponible en Oracle.");
            case CONEXION -> indisponible(error);
        };
    }

    @ExceptionHandler({TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresAtracciones.class).error("Falló el acceso a atracciones", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los atracciones.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}
