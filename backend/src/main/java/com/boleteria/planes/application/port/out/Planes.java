package com.boleteria.planes.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.planes.domain.DatosPlan;
import com.boleteria.planes.domain.Plan;

public interface Planes {
    List<Plan> listar(Long alcance);
    Optional<Plan> consultar(long id);
    Plan crear(DatosPlan datos);
    Optional<Plan> actualizar(long id, DatosPlan datos, Long alcance);
    boolean eliminarSinReservas(long id, Long alcance);

    class Fallo extends RuntimeException {
        public enum Motivo { PRESTADOR_INEXISTENTE, REFERENCIADO, CAMPO_EXTENSO, CONEXION }
        private final Motivo motivo;
        public Fallo(Motivo motivo, Throwable causa) {
            super(causa);
            this.motivo = motivo;
        }
        public Motivo motivo() { return motivo; }
    }
}
