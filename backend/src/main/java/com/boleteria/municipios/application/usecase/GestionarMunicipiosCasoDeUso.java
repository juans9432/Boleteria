package com.boleteria.municipios.application.usecase;

import java.util.Optional;
import com.boleteria.municipios.application.port.in.GestionarMunicipios;
import com.boleteria.municipios.application.port.out.PersistirMunicipios;
import com.boleteria.municipios.domain.DatosMunicipio;
import com.boleteria.municipios.domain.Municipio;

public class GestionarMunicipiosCasoDeUso implements GestionarMunicipios {
    private final PersistirMunicipios municipios;

    public GestionarMunicipiosCasoDeUso(PersistirMunicipios municipios) {
        this.municipios = municipios;
    }

    @Override
    public Optional<Municipio> consultar(long id) {
        validarId(id);
        return municipios.consultar(id);
    }

    @Override
    public Municipio crear(String nombre, String departamento) {
        return municipios.crear(new DatosMunicipio(nombre, departamento));
    }

    @Override
    public Optional<Municipio> actualizar(long id, String nombre, String departamento) {
        validarId(id);
        return municipios.actualizar(id, new DatosMunicipio(nombre, departamento));
    }

    @Override
    public boolean eliminar(long id) {
        validarId(id);
        return municipios.eliminar(id);
    }

    private static void validarId(long id) {
        if (id < 1) {
            throw new DatosMunicipio.Invalido("El ID del municipio debe ser positivo.");
        }
    }
}
