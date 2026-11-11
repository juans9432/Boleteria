package com.boleteria.combos.infrastructure.web;

import com.boleteria.combos.application.port.out.Combos.Fallo;
import com.boleteria.combos.domain.Combo;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CombosController.class)
class ErroresCombos {
    @ExceptionHandler(Combo.Invalido.class)
    ProblemDetail invalido(Combo.Invalido error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Combo.Conflicto.class)
    ProblemDetail conflicto(Combo.Conflicto error) {
        return problema(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler({Fallo.class, TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresCombos.class).error("Falló el acceso a combos", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los combos.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}




