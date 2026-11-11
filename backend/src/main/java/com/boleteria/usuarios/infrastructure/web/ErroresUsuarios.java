package com.boleteria.usuarios.infrastructure.web;

import com.boleteria.usuarios.domain.Usuario.Denegado;
import com.boleteria.usuarios.application.port.out.Usuarios.Fallo;
import com.boleteria.usuarios.domain.Usuario;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UsuariosController.class)
class ErroresUsuarios {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de usuarios.");
    }

    @ExceptionHandler(Usuario.Invalido.class)
    ProblemDetail invalido(Usuario.Invalido error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Usuario.Conflicto.class)
    ProblemDetail conflicto(Usuario.Conflicto error) {
        return problema(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler({Fallo.class, TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los usuarios.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}






