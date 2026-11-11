package com.boleteria.funciones.infrastructure.web;

import com.boleteria.funciones.application.port.in.GestionarFunciones;
import com.boleteria.funciones.application.port.in.GestionarFunciones.Denegado;
import com.boleteria.funciones.application.port.out.Funciones.Fallo;
import com.boleteria.funciones.domain.DatosFuncion;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FuncionesController.class)
class ErroresFunciones {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de funciones.");
    }

    @ExceptionHandler(DatosFuncion.Invalida.class)
    ProblemDetail invalido(DatosFuncion.Invalida error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(GestionarFunciones.Conflicto.class)
    ProblemDetail conflicto(GestionarFunciones.Conflicto error) {
        return problema(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler({Fallo.class, TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresFunciones.class).error("Falló el acceso a funciones", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los funciones.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}


