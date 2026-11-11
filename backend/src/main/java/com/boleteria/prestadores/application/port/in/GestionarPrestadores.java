package com.boleteria.prestadores.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.prestadores.domain.DatosPrestador;
import com.boleteria.prestadores.domain.Prestador;

public interface GestionarPrestadores {
    List<Prestador> listar(Acceso acceso);
    Optional<Prestador> consultar(Acceso acceso, long id);
    Prestador crear(Acceso acceso, DatosPrestador datos);
    Optional<Prestador> actualizar(Acceso acceso, long id, DatosPrestador datos);
    boolean eliminar(Acceso acceso, long id);

    record Acceso(boolean administrador, Long prestadorAsignado) {
    }

    class Denegado extends RuntimeException {
    }
}
