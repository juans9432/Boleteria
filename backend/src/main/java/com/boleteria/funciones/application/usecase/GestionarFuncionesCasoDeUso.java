package com.boleteria.funciones.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.funciones.application.port.in.GestionarFunciones;
import com.boleteria.funciones.application.port.out.Funciones;
import com.boleteria.funciones.domain.DatosFuncion;
import com.boleteria.funciones.domain.Funcion;
import com.boleteria.funciones.domain.Funcion.Estado;

public class GestionarFuncionesCasoDeUso implements GestionarFunciones {
    private final Funciones funciones;

    public GestionarFuncionesCasoDeUso(Funciones funciones) { this.funciones = funciones; }

    @Override
    public List<Funcion> listar(Acceso acceso) { return funciones.listar(alcance(acceso)); }

    @Override
    public Optional<Funcion> consultar(Acceso acceso, long id) {
        validarId(id);
        return funciones.consultar(id, false).map(funcion -> {
            autorizar(acceso, funcion.idAtraccion(), false);
            return funcion;
        });
    }

    @Override
    public Funcion crear(Acceso acceso, DatosFuncion datos) {
        autorizar(acceso, datos.idAtraccion(), true);
        comprobarCruce(0, datos);
        return funciones.crear(datos);
    }

    @Override
    public Optional<Funcion> actualizar(Acceso acceso, long id, DatosFuncion datos) {
        validarId(id);
        return funciones.consultar(id, true).map(actual -> {
            long origen = actual.idAtraccion();
            long destino = datos.idAtraccion();
            autorizar(acceso, Math.min(origen, destino), true);
            if (origen != destino) autorizar(acceso, Math.max(origen, destino), true);
            exigirProgramada(actual);
            if (funciones.tieneBoletas(id)) {
                throw new Conflicto("La función tiene boletas asociadas y su programación se conserva.");
            }
            comprobarCruce(id, datos);
            return funciones.actualizar(id, datos);
        });
    }

    @Override
    public boolean eliminar(Acceso acceso, long id) {
        validarId(id);
        return funciones.consultar(id, true).map(actual -> {
            autorizar(acceso, actual.idAtraccion(), true);
            exigirProgramada(actual);
            funciones.bloquearEscriturasDeZonas();
            if (funciones.tieneZonas(id)) {
                throw new Conflicto("La función tiene zonas asociadas y no se puede eliminar.");
            }
            return funciones.eliminar(id);
        }).orElse(false);
    }

    private void comprobarCruce(long id, DatosFuncion datos) {
        if (funciones.agenda(datos.idAtraccion(), datos.fecha()).stream()
                .anyMatch(f -> f.id() != id && f.cruza(datos))) {
            throw new Conflicto("El horario se cruza con otra función de la misma atracción.");
        }
    }

    private void autorizar(Acceso acceso, long atraccion, boolean bloquear) {
        Long alcance = alcance(acceso);
        long prestador = funciones.prestadorDeAtraccion(atraccion, bloquear)
                .orElseThrow(() -> new DatosFuncion.Invalida("La atracción seleccionada no existe."));
        if (alcance != null && alcance != prestador) throw new Denegado();
    }

    private static Long alcance(Acceso acceso) {
        if (acceso.administrador()) return null;
        if (acceso.prestadorAsignado() == null) throw new Denegado();
        return acceso.prestadorAsignado();
    }

    private static void exigirProgramada(Funcion funcion) {
        if (funcion.estado() != Estado.PROGRAMADA) throw new Conflicto("La función histórica es de solo lectura.");
    }

    private static void validarId(long id) {
        if (id < 1) throw new DatosFuncion.Invalida("El ID debe ser positivo.");
    }
}
