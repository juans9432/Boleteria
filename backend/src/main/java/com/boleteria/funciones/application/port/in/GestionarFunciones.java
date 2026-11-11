package com.boleteria.funciones.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.funciones.domain.DatosFuncion;
import com.boleteria.funciones.domain.Funcion;

public interface GestionarFunciones {
    List<Funcion> listar(Acceso acceso);
    Optional<Funcion> consultar(Acceso acceso, long id);
    Funcion crear(Acceso acceso, DatosFuncion datos);
    Optional<Funcion> actualizar(Acceso acceso, long id, DatosFuncion datos);
    boolean eliminar(Acceso acceso, long id);

    record Acceso(boolean administrador, Long prestadorAsignado) {}
    class Denegado extends RuntimeException {}
    class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) { super(mensaje); }
    }
}
