package com.boleteria.ventas.application.port.in;

import java.util.List;
import com.boleteria.reservas.domain.Reserva.Actor;
import com.boleteria.ventas.domain.Venta.*;

public interface ConfirmarVentas {
    Cotizacion cotizar(Actor actor, long reserva);
    List<Boleta> confirmar(Actor actor, long reserva, String revision);
}

