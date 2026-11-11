package com.boleteria.planes.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.planes.application.port.in.GestionarPlanes;
import com.boleteria.planes.application.port.out.Planes;
import com.boleteria.planes.domain.DatosPlan;
import com.boleteria.planes.domain.Plan;

public class GestionarPlanesCasoDeUso implements GestionarPlanes {
    private final Planes planes;

    public GestionarPlanesCasoDeUso(Planes planes) { this.planes = planes; }

    @Override
    public List<Plan> listar(Acceso acceso) { return planes.listar(alcance(acceso)); }

    @Override
    public Optional<Plan> consultar(Acceso acceso, long id) {
        validarId(id);
        Long alcance = alcance(acceso);
        return planes.consultar(id).map(plan -> {
            exigirPrestador(alcance, plan.idPrestador());
            return plan;
        });
    }

    @Override
    public Plan crear(Acceso acceso, DatosPlan datos) {
        exigirPrestador(alcance(acceso), datos.idPrestador());
        return planes.crear(datos);
    }

    @Override
    public Optional<Plan> actualizar(Acceso acceso, long id, DatosPlan datos) {
        Long alcance = alcance(acceso);
        exigirPrestador(alcance, datos.idPrestador());
        return consultar(acceso, id).flatMap(plan -> planes.actualizar(id, datos, alcance));
    }

    @Override
    public boolean eliminar(Acceso acceso, long id) {
        return consultar(acceso, id).isPresent() && planes.eliminarSinReservas(id, alcance(acceso));
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
        if (id < 1) throw new DatosPlan.Invalido("El ID debe ser positivo.");
    }
}
