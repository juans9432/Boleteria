package com.boleteria.prestadores.infrastructure.web;

import com.boleteria.prestadores.application.port.in.GestionarPrestadores.Denegado;
import com.boleteria.prestadores.application.port.out.Prestadores.Fallo;
import com.boleteria.prestadores.domain.DatosPrestador;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PrestadoresController.class)
class ErroresPrestadores {
    @ExceptionHandler(Denegado.class)
    ProblemDetail denegado() {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación de prestadores.");
    }

    @ExceptionHandler(DatosPrestador.Invalido.class)
    ProblemDetail invalido(DatosPrestador.Invalido error) {
        return problema(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(Fallo.class)
    ProblemDetail fallo(Fallo error) {
        return switch (error.motivo()) {
            case NIT_DUPLICADO -> problema(HttpStatus.CONFLICT, "Ya existe un prestador con ese NIT.");
            case REFERENCIADO -> problema(HttpStatus.CONFLICT, "El prestador tiene planes, atracciones o cuentas asociadas.");
            case CATALOGO_INEXISTENTE -> problema(HttpStatus.BAD_REQUEST, "El municipio o tipo de prestador seleccionado no existe.");
            case CAMPO_EXTENSO -> problema(HttpStatus.BAD_REQUEST, "Un campo supera el espacio disponible en Oracle.");
            case CONEXION -> indisponible(error);
        };
    }

    @ExceptionHandler({TransactionException.class, DataAccessException.class})
    ProblemDetail indisponible(RuntimeException error) {
        LoggerFactory.getLogger(ErroresPrestadores.class).error("Falló el acceso a prestadores", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible acceder a los prestadores.");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        return ProblemDetail.forStatusAndDetail(estado, detalle);
    }
}
