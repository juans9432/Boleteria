package com.boleteria.boletas.application.usecase;

import java.time.Clock;
import java.util.List;
import com.boleteria.boletas.application.port.in.GestionarBoletas;
import com.boleteria.boletas.application.port.out.Boletas;
import com.boleteria.boletas.application.port.out.Boletas.Alcance;
import com.boleteria.boletas.domain.Boleta;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.reservas.domain.Reserva.Actor;

public final class GestionarBoletasCasoDeUso implements GestionarBoletas {
    private final Boletas boletas;
    private final Clock reloj;
    public GestionarBoletasCasoDeUso(Boletas boletas, Clock reloj) {
        this.boletas = boletas;
        this.reloj = reloj;
    }
    public List<Boleta> listar(Actor actor) { return boletas.listar(alcance(actor)); }
    public Boleta consultar(Actor actor, long id) {
        return boletas.consultar(alcance(actor), id).orElseThrow(Ausente::new);
    }
    public Boleta porCodigo(Actor actor, String codigo) {
        if (codigo == null || codigo.isBlank() || codigo.strip().length() > 36) throw new Ausente();
        return boletas.porCodigo(alcance(actor), codigo.strip()).orElseThrow(Ausente::new);
    }
    public Boleta usar(Actor actor, long id) {
        if (actor.rol() == Rol.CLIENTE) throw new Denegado();
        var candidata = consultar(actor, id);
        boletas.bloquearUso(candidata.funcion(), id);
        var actual = consultar(actor, id);
        actual.validarUso(reloj.instant());
        if (!boletas.marcarUsada(id)) throw new Conflicto("La boleta ya fue procesada.");
        return consultar(actor, id);
    }
    private static Alcance alcance(Actor actor) {
        return switch (actor.rol()) {
            case ADMINISTRADOR, OPERADOR -> new Alcance(null, null);
            case CLIENTE -> new Alcance(actor.usuario(), null);
            case ENCARGADO_PRESTADOR -> {
                if (actor.prestador() == null) throw new Denegado();
                yield new Alcance(null, actor.prestador());
            }
        };
    }
}

