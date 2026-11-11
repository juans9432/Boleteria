package com.boleteria.zonas.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.zonas.domain.Zona;

public interface Zonas {
    List<Zona> listar(Long prestador, Long idFuncion);
    Optional<Zona> consultar(long id, boolean bloquear);
    Optional<Funcion> funcion(long id, boolean bloquear);
    Zona crear(long idFuncion, Zona.Datos datos);
    Zona actualizar(Zona actual, Zona.Datos datos);
    long ocupacion(long id);
    boolean tieneBoletas(long id);
    boolean eliminar(long id);

    record Funcion(long idPrestador, String estado) {}

    class Fallo extends RuntimeException {
        public Fallo(Throwable causa) { super(causa); }
    }
}
