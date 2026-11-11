package com.boleteria.identidad.application.port.in;

import java.util.Optional;
import com.boleteria.identidad.application.port.out.Cuentas.Credencial;
import com.boleteria.identidad.domain.Cuenta;

public interface ConsultarIdentidad {
    Optional<Credencial> buscarCredencial(String correo);
    Optional<Cuenta> consultarCuenta(String correo);
    boolean puedeAdministrar(String correo, long idPrestador);
}
