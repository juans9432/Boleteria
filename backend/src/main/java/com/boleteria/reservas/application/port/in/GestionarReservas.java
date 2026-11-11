package com.boleteria.reservas.application.port.in;

import java.util.List;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.Actor;
import com.boleteria.reservas.domain.Reserva.Solicitud;

public interface GestionarReservas {
    com.boleteria.reservas.application.port.out.Reservas.Catalogos catalogos(Actor actor);
    List<Reserva> listar(Actor actor);
    Reserva consultar(Actor actor, long id);
    Reserva crear(Actor actor, Solicitud solicitud);
    Reserva editar(Actor actor, long id, Solicitud solicitud);
    Reserva cancelar(Actor actor, long id);
    int expirar();
}


