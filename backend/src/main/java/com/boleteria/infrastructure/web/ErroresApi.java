package com.boleteria.infrastructure.web;

import com.boleteria.municipios.application.port.out.ConsultarMunicipios.ConsultaFallida;
import com.boleteria.municipios.application.port.out.PersistirMunicipios.MunicipioReferenciado;
import com.boleteria.municipios.domain.DatosMunicipio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ErroresApi {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresApi.class);

    @ExceptionHandler(ConsultaFallida.class)
    ProblemDetail consultaFallida(ConsultaFallida error) {
        LOG.error("Falló la consulta de municipios", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No fue posible consultar los municipios.");
    }

    @ExceptionHandler(DatosMunicipio.Invalido.class)
    ProblemDetail datosInvalidos(DatosMunicipio.Invalido error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(MunicipioReferenciado.class)
    ProblemDetail municipioReferenciado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El municipio tiene prestadores asociados y no se puede eliminar.");
    }
}
