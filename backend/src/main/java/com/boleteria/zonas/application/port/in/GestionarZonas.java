package com.boleteria.zonas.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.zonas.domain.Zona;

public interface GestionarZonas {
    List<Zona> listar(Acceso acceso, Long idFuncion);
    Optional<Zona> consultar(Acceso acceso, long id);
    Zona crear(Acceso acceso, long idFuncion, Zona.Datos datos);
    Optional<Zona> actualizar(Acceso acceso, long id, Zona.Datos datos);
    boolean eliminar(Acceso acceso, long id);

    record Acceso(boolean administrador, Long prestadorAsignado) {}
    class Denegado extends RuntimeException {}
}
