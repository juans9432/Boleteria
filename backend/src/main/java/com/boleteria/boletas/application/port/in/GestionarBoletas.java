package com.boleteria.boletas.application.port.in;

import java.util.List;
import com.boleteria.boletas.domain.Boleta;
import com.boleteria.reservas.domain.Reserva.Actor;

public interface GestionarBoletas {
    List<Boleta> listar(Actor actor);
    Boleta consultar(Actor actor, long id);
    Boleta porCodigo(Actor actor, String codigo);
    Boleta usar(Actor actor, long id);
}

