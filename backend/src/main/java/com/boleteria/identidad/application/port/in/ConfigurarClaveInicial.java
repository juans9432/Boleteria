package com.boleteria.identidad.application.port.in;

public interface ConfigurarClaveInicial {
    boolean ejecutar(String correo, String clave);
}
