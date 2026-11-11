package com.boleteria.combos.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.combos.application.port.in.GestionarCombos;
import com.boleteria.combos.application.port.out.Combos;
import com.boleteria.combos.domain.Combo;
import com.boleteria.combos.domain.Combo.Estado;

public class GestionarCombosCasoDeUso implements GestionarCombos {
    private final Combos combos;

    public GestionarCombosCasoDeUso(Combos combos) { this.combos = combos; }

    @Override
    public List<Combo> listar() { return combos.listar(); }

    @Override
    public Optional<Combo> consultar(long id) {
        validarId(id);
        return combos.consultar(id, false);
    }

    @Override
    public Combo crear(Combo.Datos datos) {
        if (datos.estado() != Estado.INACTIVO) throw new Combo.Invalido("Crea el combo como INACTIVO antes de activarlo.");
        long id = combos.insertar(datos);
        combos.reemplazarAtracciones(id, datos.atracciones());
        return new Combo(id, datos);
    }

    @Override
    public Optional<Combo> actualizar(long id, Combo.Datos datos) {
        validarId(id);
        return combos.consultar(id, true).map(actual -> {
            boolean cambiaComposicion = !actual.datos().atracciones().equals(datos.atracciones());
            if (cambiaComposicion && actual.datos().estado() == Estado.ACTIVO) {
                throw new Combo.Conflicto("Inactiva primero el combo para cambiar sus atracciones.");
            }
            datos.verificarActivacion();
            if (cambiaComposicion) combos.reemplazarAtracciones(id, datos.atracciones());
            combos.actualizar(id, datos);
            return new Combo(id, datos);
        });
    }

    @Override
    public boolean eliminar(long id) {
        validarId(id);
        return combos.consultar(id, true).map(actual -> {
            if (combos.tieneBoletas(id)) {
                throw new Combo.Conflicto("El combo tiene boletas asociadas. Puedes inactivarlo.");
            }
            return combos.eliminar(id);
        }).orElse(false);
    }

    private static void validarId(long id) {
        if (id < 1) throw new Combo.Invalido("El ID debe ser positivo.");
    }
}
