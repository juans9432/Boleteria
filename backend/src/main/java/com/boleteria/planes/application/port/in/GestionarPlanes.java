package com.boleteria.planes.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.planes.domain.DatosPlan;
import com.boleteria.planes.domain.Plan;

public interface GestionarPlanes {
    List<Plan> listar(Acceso acceso);
    Optional<Plan> consultar(Acceso acceso, long id);
    Plan crear(Acceso acceso, DatosPlan datos);
    Optional<Plan> actualizar(Acceso acceso, long id, DatosPlan datos);
    boolean eliminar(Acceso acceso, long id);

    record Acceso(boolean administrador, Long prestadorAsignado) {}
    class Denegado extends RuntimeException {}
}
