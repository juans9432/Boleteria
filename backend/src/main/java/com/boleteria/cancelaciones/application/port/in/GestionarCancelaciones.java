package com.boleteria.cancelaciones.application.port.in;
import com.boleteria.reservas.domain.Reserva.Actor;

public interface GestionarCancelaciones {
    void anularBoleta(Actor actor, long id);
    void anularCombo(Actor actor, long id);
    void cancelarReserva(Actor actor, long id);
    void cancelarFuncion(Actor actor, long id);
}

