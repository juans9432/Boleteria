package com.boleteria.combos.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.combos.domain.Combo;

public interface Combos {
    List<Combo> listar();
    Optional<Combo> consultar(long id, boolean bloquear);
    long insertar(Combo.Datos datos);
    void actualizar(long id, Combo.Datos datos);
    void reemplazarAtracciones(long id, List<Combo.Atraccion> atracciones);
    boolean tieneBoletas(long id);
    boolean eliminar(long id);

    class Fallo extends RuntimeException {
        public Fallo(Throwable causa) { super(causa); }
    }
}
