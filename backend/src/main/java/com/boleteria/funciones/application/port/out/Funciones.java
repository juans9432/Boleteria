package com.boleteria.funciones.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.boleteria.funciones.domain.DatosFuncion;
import com.boleteria.funciones.domain.Funcion;

public interface Funciones {
    List<Funcion> listar(Long prestador);
    Optional<Funcion> consultar(long id, boolean bloquear);
    Optional<Long> prestadorDeAtraccion(long idAtraccion, boolean bloquear);
    List<Funcion> agenda(long idAtraccion, LocalDate fecha);
    Funcion crear(DatosFuncion datos);
    Funcion actualizar(long id, DatosFuncion datos);
    void bloquearEscriturasDeZonas();
    boolean tieneZonas(long idFuncion);
    boolean tieneBoletas(long idFuncion);
    boolean eliminar(long id);

    class Fallo extends RuntimeException {
        public Fallo(Throwable causa) { super(causa); }
    }
}
