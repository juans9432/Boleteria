package com.boleteria.zonas.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.zonas.application.port.in.GestionarZonas;
import com.boleteria.zonas.application.port.out.Zonas;
import com.boleteria.zonas.domain.Zona;

public class GestionarZonasCasoDeUso implements GestionarZonas {
    private final Zonas zonas;

    public GestionarZonasCasoDeUso(Zonas zonas) { this.zonas = zonas; }

    @Override
    public List<Zona> listar(Acceso acceso, Long idFuncion) {
        Long alcance = alcance(acceso);
        if (idFuncion != null) autorizar(acceso, idFuncion, false);
        return zonas.listar(alcance, idFuncion);
    }

    @Override
    public Optional<Zona> consultar(Acceso acceso, long id) {
        validarId(id);
        alcance(acceso);
        return zonas.consultar(id, false).map(zona -> {
            autorizar(acceso, zona.idFuncion(), false);
            return zona;
        });
    }

    @Override
    public Zona crear(Acceso acceso, long idFuncion, Zona.Datos datos) {
        autorizar(acceso, idFuncion, true);
        return zonas.crear(idFuncion, datos);
    }

    @Override
    public Optional<Zona> actualizar(Acceso acceso, long id, Zona.Datos datos) {
        return bloquearZonaEditable(acceso, id).map(zona -> {
            datos.verificarOcupacion(zonas.ocupacion(id));
            return zonas.actualizar(zona, datos);
        });
    }

    @Override
    public boolean eliminar(Acceso acceso, long id) {
        return bloquearZonaEditable(acceso, id).map(zona -> {
            if (zonas.tieneBoletas(id)) throw new Zona.Conflicto("La zona tiene boletas asociadas y no se puede eliminar.");
            return zonas.eliminar(id);
        }).orElse(false);
    }

    private Optional<Zona> bloquearZonaEditable(Acceso acceso, long id) {
        validarId(id);
        alcance(acceso);
        return zonas.consultar(id, false).flatMap(zona -> {
            autorizar(acceso, zona.idFuncion(), true);
            return zonas.consultar(id, true);
        });
    }

    private void autorizar(Acceso acceso, long idFuncion, boolean escritura) {
        validarId(idFuncion);
        Long alcance = alcance(acceso);
        var funcion = zonas.funcion(idFuncion, escritura)
                .orElseThrow(() -> new Zona.Invalida("La función seleccionada no existe."));
        if (alcance != null && alcance != funcion.idPrestador()) throw new Denegado();
        if (escritura && !"PROGRAMADA".equals(funcion.estado())) {
            throw new Zona.Conflicto("Las zonas de una función cancelada o realizada son de solo lectura.");
        }
    }

    private static Long alcance(Acceso acceso) {
        if (acceso.administrador()) return null;
        if (acceso.prestadorAsignado() == null) throw new Denegado();
        return acceso.prestadorAsignado();
    }

    private static void validarId(long id) {
        if (id < 1) throw new Zona.Invalida("El ID debe ser positivo.");
    }
}
