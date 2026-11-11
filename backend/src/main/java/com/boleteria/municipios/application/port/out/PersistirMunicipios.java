package com.boleteria.municipios.application.port.out;

import java.util.Optional;
import com.boleteria.municipios.domain.DatosMunicipio;
import com.boleteria.municipios.domain.Municipio;

public interface PersistirMunicipios {
    Optional<Municipio> consultar(long id);
    Municipio crear(DatosMunicipio datos);
    Optional<Municipio> actualizar(long id, DatosMunicipio datos);
    boolean eliminar(long id);

    class MunicipioReferenciado extends RuntimeException {
        public MunicipioReferenciado(Throwable causa) {
            super(causa);
        }
    }
}
