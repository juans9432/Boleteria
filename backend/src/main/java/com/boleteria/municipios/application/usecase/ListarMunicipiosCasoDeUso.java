package com.boleteria.municipios.application.usecase;

import java.util.List;
import com.boleteria.municipios.application.port.in.ListarMunicipios;
import com.boleteria.municipios.application.port.out.ConsultarMunicipios;
import com.boleteria.municipios.domain.Municipio;

public class ListarMunicipiosCasoDeUso implements ListarMunicipios {
    private final ConsultarMunicipios municipios;

    public ListarMunicipiosCasoDeUso(ConsultarMunicipios municipios) {
        this.municipios = municipios;
    }

    @Override
    public List<Municipio> ejecutar() {
        return municipios.listar();
    }
}
