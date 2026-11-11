package com.boleteria.municipios.application.port.in;

import java.util.List;
import com.boleteria.municipios.domain.Municipio;

public interface ListarMunicipios {
    List<Municipio> ejecutar();
}
