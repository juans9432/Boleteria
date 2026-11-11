package com.boleteria.municipios.application.port.in;

import java.util.Optional;
import com.boleteria.municipios.domain.Municipio;

public interface GestionarMunicipios {
    Optional<Municipio> consultar(long id);
    Municipio crear(String nombre, String departamento);
    Optional<Municipio> actualizar(long id, String nombre, String departamento);
    boolean eliminar(long id);
}
