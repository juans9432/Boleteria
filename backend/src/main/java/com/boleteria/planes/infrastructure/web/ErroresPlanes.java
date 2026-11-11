package com.boleteria.planes.infrastructure.web;

import com.boleteria.planes.application.port.in.GestionarPlanes.Denegado;
import com.boleteria.planes.application.port.out.Planes.Fallo;
import com.boleteria.planes.domain.DatosPlan;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PlanesController.class)
class ErroresPlanes {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de planes.");
    }

    @ExceptionHandler(DatosPlan.Invalido.class)
    ProblemDetail invalido(DatosPlan.Invalido error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Fallo.class)
    ProblemDetail fallo(Fallo error) {
        return switch (error.motivo()) {
            
            case REFERENCIADO -> problema(HttpStatus.CONFLICT, "El plan tiene reservas asociadas. Puedes cambiar su estado a INACTIVO.");
            case PRESTADOR_INEXISTENTE -> problema(HttpStatus.BAD_REQUEST, "El prestador seleccionado no existe.");
            case CAMPO_EXTENSO -> problema(HttpStatus.BAD_REQUEST, "Un campo supera el espacio disponible en Oracle.");
            case CONEXION -> indisponible(error);
        };
    }

    @ExceptionHandler({TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresPlanes.class).error("Falló el acceso a planes", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los planes.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}

