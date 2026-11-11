package com.boleteria.identidad.application.usecase;

import java.nio.charset.StandardCharsets;
import com.boleteria.identidad.application.port.in.ConfigurarClaveInicial;
import com.boleteria.identidad.application.port.out.CodificarClave;
import com.boleteria.identidad.application.port.out.Cuentas;

public class ProvisionarClave implements ConfigurarClaveInicial {
    private final Cuentas cuentas;
    private final CodificarClave codificador;

    public ProvisionarClave(Cuentas cuentas, CodificarClave codificador) {
        this.cuentas = cuentas;
        this.codificador = codificador;
    }

    @Override
    public boolean ejecutar(String correo, String clave) {
        if (correo == null || correo.isBlank() || clave == null || clave.isBlank()
                || clave.length() < 12 || clave.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Indique un correo y una clave de al menos 12 caracteres y hasta 72 bytes.");
        }
        return cuentas.configurarClaveInicial(correo.strip(), codificador.codificar(clave));
    }
}
