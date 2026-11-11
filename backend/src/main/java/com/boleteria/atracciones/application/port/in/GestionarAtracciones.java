package com.boleteria.atracciones.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.atracciones.domain.DatosAtraccion;
import com.boleteria.atracciones.domain.Atraccion;

public interface GestionarAtracciones {
    List<Atraccion> listar(Acceso acceso);
    Optional<Atraccion> consultar(Acceso acceso, long id);
    Atraccion crear(Acceso acceso, DatosAtraccion datos);
    Optional<Atraccion> actualizar(Acceso acceso, long id, DatosAtraccion datos);
    boolean eliminar(Acceso acceso, long id);

    record Acceso(boolean administrador, Long prestadorAsignado) {}
    class Denegado extends RuntimeException {}
}
