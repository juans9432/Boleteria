package com.boleteria.tiposentrada.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.tiposentrada.domain.TipoEntrada;

public interface GestionarTiposEntrada {
    List<TipoEntrada> listar();
    Optional<TipoEntrada> consultar(long id);
    TipoEntrada crear(TipoEntrada.Datos datos);
    Optional<TipoEntrada> actualizar(long id, TipoEntrada.Datos datos);
    boolean eliminar(long id);
}

