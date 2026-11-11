package com.boleteria.identidad.application.port.out;

import java.util.Optional;
import com.boleteria.identidad.domain.Cuenta;

public interface Cuentas {
    Optional<Credencial> buscarPorCorreo(String correo);
    boolean configurarClaveInicial(String correo, String hash);

    record Credencial(Cuenta cuenta, String hash) {
        @Override
        public String toString() {
            return "Credencial[cuenta=" + cuenta + "]";
        }
    }
}
