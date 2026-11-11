package com.boleteria.identidad.application.usecase;

import java.util.Optional;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.identidad.application.port.out.Cuentas;
import com.boleteria.identidad.application.port.out.Cuentas.Credencial;
import com.boleteria.identidad.domain.Cuenta;

public class Identidad implements ConsultarIdentidad {
    private final Cuentas cuentas;

    public Identidad(Cuentas cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public Optional<Credencial> buscarCredencial(String correo) {
        if (correo == null || correo.isBlank() || correo.strip().length() > 100) {
            return Optional.empty();
        }
        return cuentas.buscarPorCorreo(correo.strip());
    }

    @Override
    public Optional<Cuenta> consultarCuenta(String correo) {
        return buscarCredencial(correo).map(Credencial::cuenta).filter(Cuenta::habilitada);
    }

    @Override
    public boolean puedeAdministrar(String correo, long idPrestador) {
        return consultarCuenta(correo).filter(c -> c.puedeAdministrar(idPrestador)).isPresent();
    }
}
