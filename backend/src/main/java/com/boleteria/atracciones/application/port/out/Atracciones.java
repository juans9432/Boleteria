package com.boleteria.atracciones.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.atracciones.domain.DatosAtraccion;
import com.boleteria.atracciones.domain.Atraccion;

public interface Atracciones {
    List<Atraccion> listar(Long alcance);
    Optional<Atraccion> consultar(long id);
    Atraccion crear(DatosAtraccion datos);
    Optional<Atraccion> actualizar(long id, DatosAtraccion datos, Long alcance);
    boolean eliminar(long id, Long alcance);

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
