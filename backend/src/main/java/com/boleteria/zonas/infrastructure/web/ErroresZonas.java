package com.boleteria.zonas.infrastructure.web;

import com.boleteria.zonas.application.port.in.GestionarZonas;
import com.boleteria.zonas.application.port.in.GestionarZonas.Denegado;
import com.boleteria.zonas.application.port.out.Zonas.Fallo;
import com.boleteria.zonas.domain.Zona;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ZonasController.class)
class ErroresZonas {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de zonas.");
    }

    @ExceptionHandler(Zona.Invalida.class)
    ProblemDetail invalido(Zona.Invalida error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Zona.Conflicto.class)
    ProblemDetail conflicto(Zona.Conflicto error) {
        return problema(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler({Fallo.class, TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresZonas.class).error("Falló el acceso a zonas", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los zonas.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}



