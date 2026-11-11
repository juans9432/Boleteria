package com.boleteria.tiposentrada.infrastructure.web;

import com.boleteria.tiposentrada.application.port.out.TiposEntrada.*;
import com.boleteria.tiposentrada.domain.TipoEntrada;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TiposEntradaController.class)
class ErroresTiposEntrada {
    @ExceptionHandler(TipoEntrada.SemanticaHistorica.class)
    ProblemDetail semantica() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El tipo tiene boletas históricas. Conserva su clasificación de adulto y su requisito de acompañamiento.");
    }

    @ExceptionHandler(TipoEntrada.Invalido.class)
    ProblemDetail invalido(TipoEntrada.Invalido error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(NombreDuplicado.class)
    ProblemDetail duplicado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ya existe un tipo de entrada con ese nombre.");
    }

    @ExceptionHandler(TipoReferenciado.class)
    ProblemDetail referenciado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El tipo de entrada tiene boletas asociadas y no se puede eliminar.");
    }

    @ExceptionHandler({PersistenciaFallida.class, org.springframework.transaction.TransactionException.class})
    ProblemDetail persistencia(RuntimeException error) {
        LoggerFactory.getLogger(ErroresTiposEntrada.class).error("Falló el acceso a tipos de entrada", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No fue posible acceder a los tipos de entrada.");
    }
}

