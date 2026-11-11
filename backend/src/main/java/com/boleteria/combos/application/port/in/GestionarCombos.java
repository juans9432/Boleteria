package com.boleteria.combos.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.combos.domain.Combo;

public interface GestionarCombos {
    List<Combo> listar();
    Optional<Combo> consultar(long id);
    Combo crear(Combo.Datos datos);
    Optional<Combo> actualizar(long id, Combo.Datos datos);
    boolean eliminar(long id);
}
