package com.boleteria.atracciones.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.atracciones.application.port.in.GestionarAtracciones;
import com.boleteria.atracciones.application.port.out.Atracciones;
import com.boleteria.atracciones.domain.DatosAtraccion;
import com.boleteria.atracciones.domain.Atraccion;

public class GestionarAtraccionesCasoDeUso implements GestionarAtracciones {
    private final Atracciones atracciones;

    public GestionarAtraccionesCasoDeUso(Atracciones atracciones) { this.atracciones = atracciones; }

    @Override
    public List<Atraccion> listar(Acceso acceso) { return atracciones.listar(alcance(acceso)); }

    @Override
    public Optional<Atraccion> consultar(Acceso acceso, long id) {
        validarId(id);
        Long alcance = alcance(acceso);
        return atracciones.consultar(id).map(atraccion -> {
            exigirPrestador(alcance, atraccion.idPrestador());
            return atraccion;
        });
    }

    @Override
    public Atraccion crear(Acceso acceso, DatosAtraccion datos) {
        exigirPrestador(alcance(acceso), datos.idPrestador());
        return atracciones.crear(datos);
    }

    @Override
    public Optional<Atraccion> actualizar(Acceso acceso, long id, DatosAtraccion datos) {
        Long alcance = alcance(acceso);
        exigirPrestador(alcance, datos.idPrestador());
        return consultar(acceso, id).flatMap(atraccion -> atracciones.actualizar(id, datos, alcance));
    }

    @Override
    public boolean eliminar(Acceso acceso, long id) {
        return consultar(acceso, id).isPresent() && atracciones.eliminar(id, alcance(acceso));
    }

    private static Long alcance(Acceso acceso) {
        if (acceso.administrador()) return null;
        if (acceso.prestadorAsignado() == null) throw new Denegado();
        return acceso.prestadorAsignado();
    }

    private static void exigirPrestador(Long alcance, long id) {
        if (alcance != null && alcance != id) throw new Denegado();
    }

    private static void validarId(long id) {
        if (id < 1) throw new DatosAtraccion.Invalido("El ID debe ser positivo.");
    }
}
