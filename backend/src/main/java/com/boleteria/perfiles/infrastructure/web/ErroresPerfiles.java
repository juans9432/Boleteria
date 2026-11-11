package com.boleteria.perfiles.infrastructure.web;

import com.boleteria.perfiles.application.port.in.GestionarPerfiles;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Denegado;
import com.boleteria.perfiles.application.port.out.Perfiles.Fallo;
import com.boleteria.perfiles.domain.Perfil;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PerfilesController.class)
class ErroresPerfiles {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de perfiles.");
    }

    @ExceptionHandler(Perfil.Invalido.class)
    ProblemDetail invalido(Perfil.Invalido error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Perfil.Conflicto.class)
    ProblemDetail conflicto(Perfil.Conflicto error) {
        return problema(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler({Fallo.class, TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresPerfiles.class).error("Falló el acceso a perfiles", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los perfiles.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}




