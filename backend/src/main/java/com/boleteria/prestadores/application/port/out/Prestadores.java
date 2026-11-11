package com.boleteria.prestadores.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.prestadores.domain.DatosPrestador;
import com.boleteria.prestadores.domain.Prestador;

public interface Prestadores {
    List<Prestador> listar();
    Optional<Prestador> consultar(long id);
    Prestador crear(DatosPrestador datos);
    Optional<Prestador> actualizar(long id, DatosPrestador datos);
    boolean eliminar(long id);

    class Fallo extends RuntimeException {
        public enum Motivo { NIT_DUPLICADO, CATALOGO_INEXISTENTE, REFERENCIADO, CAMPO_EXTENSO, CONEXION }
        private final Motivo motivo;
        public Fallo(Motivo motivo, Throwable causa) {
            super(causa);
            this.motivo = motivo;
        }
        public Motivo motivo() { return motivo; }
    }
}
