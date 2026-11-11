package com.boleteria.tiposprestador.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.tiposprestador.domain.TipoPrestador;

public interface TiposPrestador {
    List<TipoPrestador> listar();
    Optional<TipoPrestador> consultar(long id);
    TipoPrestador crear(String nombre);
    boolean actualizar(TipoPrestador tipo);
    boolean eliminar(long id);

    class NombreDuplicado extends RuntimeException {
        public NombreDuplicado(Throwable causa) { super(causa); }
    }

    class TipoReferenciado extends RuntimeException {
        public TipoReferenciado(Throwable causa) { super(causa); }
    }

    class PersistenciaFallida extends RuntimeException {
        public PersistenciaFallida(Throwable causa) { super(causa); }
    }
}
