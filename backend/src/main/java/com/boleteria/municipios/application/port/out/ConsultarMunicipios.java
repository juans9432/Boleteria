package com.boleteria.municipios.application.port.out;

import java.util.List;
import com.boleteria.municipios.domain.Municipio;

public interface ConsultarMunicipios {
    List<Municipio> listar();

    class ConsultaFallida extends RuntimeException {
        public ConsultaFallida(Throwable causa) {
            super(causa);
        }
    }
}
