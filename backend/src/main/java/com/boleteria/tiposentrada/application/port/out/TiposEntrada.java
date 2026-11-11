package com.boleteria.tiposentrada.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.tiposentrada.domain.TipoEntrada;

public interface TiposEntrada {
    List<TipoEntrada> listar();
    Optional<TipoEntrada> consultar(long id);
    Optional<TipoEntrada> bloquear(long id);
    boolean tieneBoletas(long id);
    TipoEntrada crear(TipoEntrada.Datos datos);
    boolean actualizar(TipoEntrada tipo);
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


