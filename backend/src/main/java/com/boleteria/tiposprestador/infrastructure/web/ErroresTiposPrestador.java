package com.boleteria.tiposprestador.infrastructure.web;

import com.boleteria.tiposprestador.application.port.out.TiposPrestador.*;
import com.boleteria.tiposprestador.domain.TipoPrestador;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TiposPrestadorController.class)
class ErroresTiposPrestador {
    @ExceptionHandler(TipoPrestador.Invalido.class)
    ProblemDetail invalido(TipoPrestador.Invalido error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(NombreDuplicado.class)
    ProblemDetail duplicado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ya existe un tipo de prestador con ese nombre.");
    }

    @ExceptionHandler(TipoReferenciado.class)
    ProblemDetail referenciado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El tipo tiene prestadores asociados.");
    }

    @ExceptionHandler({PersistenciaFallida.class, org.springframework.transaction.TransactionException.class})
    ProblemDetail persistencia(RuntimeException error) {
        LoggerFactory.getLogger(ErroresTiposPrestador.class).error("Falló el acceso a tipos de prestador", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No fue posible acceder a los tipos de prestador.");
    }
}
