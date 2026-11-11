package com.boleteria.tiposprestador.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.tiposprestador.domain.TipoPrestador;

public interface GestionarTiposPrestador {
    List<TipoPrestador> listar();
    Optional<TipoPrestador> consultar(long id);
    TipoPrestador crear(String nombre);
    Optional<TipoPrestador> actualizar(long id, String nombre);
    boolean eliminar(long id);
}
