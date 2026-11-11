package com.boleteria.cancelaciones.application.usecase;

import java.util.*;
import java.util.stream.Collectors;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.cancelaciones.application.port.in.GestionarCancelaciones;
import com.boleteria.cancelaciones.application.port.out.Cancelaciones;
import com.boleteria.cancelaciones.domain.Cancelacion;
import com.boleteria.cancelaciones.domain.Cancelacion.Entrada;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.notificaciones.application.port.in.PublicarAvisos;
import com.boleteria.notificaciones.domain.Aviso.Evento;
import com.boleteria.reservas.application.port.out.Reservas;
import com.boleteria.reservas.domain.Reserva.Actor;

public final class GestionarCancelacionesCasoDeUso implements GestionarCancelaciones {
    private final Reservas reservas;
    private final Cancelaciones datos;
    private final PublicarAvisos avisos;
    public GestionarCancelacionesCasoDeUso(Reservas reservas, Cancelaciones datos, PublicarAvisos avisos) {
        this.reservas = reservas;
        this.datos = datos;
        this.avisos = avisos;
    }

    public void anularBoleta(Actor actor, long id) { anular(actor, id, false); }
    public void anularCombo(Actor actor, long id) { anular(actor, id, true); }

    private void anular(Actor actor, long id, boolean combo) {
        exigirOperador(actor);
        var inicial = datos.entrada(id).orElseThrow(Ausente::new);
        autorizar(actor, inicial.prestador());
        bloquearConfirmada(inicial.reserva());
        var entradas = bloquearEntradasReserva(inicial.reserva(), null);
        var objetivo = entradas.stream().filter(e -> e.id() == id).findFirst().orElseThrow(Ausente::new);
        autorizar(actor, objetivo.prestador());
        if (objetivo.estado() != Estado.VENDIDA) throw new Conflicto("La boleta debe estar VENDIDA.");
        List<Entrada> afectadas;
        if (combo) {
            if (objetivo.combo() == null || objetivo.grupo() == null) {
                throw new Conflicto("La boleta no pertenece a una unidad de combo identificable.");
            }
            afectadas = entradas.stream().filter(e -> Objects.equals(e.combo(), objetivo.combo())
                    && Objects.equals(e.grupo(), objetivo.grupo())).toList();
            afectadas.forEach(e -> autorizar(actor, e.prestador()));
        } else {
            if (objetivo.combo() != null) throw new Conflicto("Usa la anulación de la unidad de combo.");
            afectadas = List.of(objetivo);
        }
        Cancelacion.protegerUsadas(afectadas);
        var ids = vendidas(afectadas);
        var funcionesAfectadas = afectadas.stream().map(Entrada::funcion).collect(Collectors.toSet());
        Cancelacion.comprobarAcompanamiento(entradas.stream()
                .filter(e -> funcionesAfectadas.contains(e.funcion())).toList(), Set.copyOf(ids));
        datos.anular(ids);
        String operacion = combo ? "COMBO:" + objetivo.combo() + ":" + objetivo.grupo() : "BOLETA:" + id;
        publicarAnulacion(inicial.reserva(), operacion, combo ? "Unidad de combo anulada" : "Boleta anulada",
                afectadas.stream().filter(e -> e.estado() == Estado.VENDIDA).toList());
    }

    public void cancelarReserva(Actor actor, long id) {
        exigirOperador(actor);
        if (!reservas.visible(actor, id)) throw new Ausente();
        var reserva = bloquearConfirmada(id);
        var entradas = bloquearEntradasReserva(id, reserva.plan());
        if (!reservas.visible(actor, id)) throw new Ausente();
        entradas.forEach(e -> autorizar(actor, e.prestador()));
        Cancelacion.protegerUsadas(entradas);
        datos.anular(vendidas(entradas));
        datos.cancelarReserva(id);
        avisos.publicar(Evento.cancelada(id, vendidas(entradas).size()));
    }

    public void cancelarFuncion(Actor actor, long id) {
        if (actor.rol() != Rol.ADMINISTRADOR && actor.rol() != Rol.ENCARGADO_PRESTADOR) throw new Denegado();
        autorizar(actor, datos.funcion(id).orElseThrow(Ausente::new).prestador());
        datos.bloquearFunciones(List.of(id));
        var funcion = datos.funcion(id).orElseThrow(Ausente::new);
        datos.bloquearAtracciones(List.of(funcion.atraccion()));
        funcion = datos.funcion(id).orElseThrow(Ausente::new);
        autorizar(actor, funcion.prestador());
        if (!"PROGRAMADA".equals(funcion.estado())) throw new Conflicto("La función debe estar PROGRAMADA.");
        datos.bloquearBoletas(datos.deFuncion(id).stream().map(Entrada::id).sorted().toList());
        var entradas = datos.deFuncion(id);
        Cancelacion.protegerUsadas(entradas);
        datos.anular(vendidas(entradas));
        datos.cancelarFuncion(id);
        entradas.stream().filter(e -> e.estado() == Estado.VENDIDA)
                .collect(Collectors.groupingBy(Entrada::reserva, TreeMap::new, Collectors.toList()))
                .forEach((reserva, afectadas) -> publicarAnulacion(reserva, "FUNCION:" + id,
                        "Función #" + id + " cancelada", afectadas));
    }

    private void publicarAnulacion(long reserva, String operacion, String asunto, List<Entrada> afectadas) {
        var vigentes = datos.deReserva(reserva).stream().filter(e -> e.estado() != Estado.ANULADA)
                .map(Entrada::codigo).toList();
        avisos.publicar(Evento.anulacion(reserva, operacion, asunto,
                afectadas.stream().map(Entrada::codigo).toList(), vigentes));
    }

    private com.boleteria.reservas.domain.Reserva bloquearConfirmada(long id) {
        var reserva = reservas.consultar(id, true).orElseThrow(Ausente::new);
        if (reserva.estado() != com.boleteria.reservas.domain.Reserva.Estado.CONFIRMADA) throw new Conflicto("La reserva debe estar CONFIRMADA.");
        return reserva;
    }
    private List<Entrada> bloquearEntradasReserva(long id, Long plan) {
        var funciones = datos.deReserva(id).stream().map(Entrada::funcion).distinct().sorted().toList();
        datos.bloquearFunciones(funciones);
        datos.bloquearAtracciones(funciones.stream().map(f -> datos.funcion(f).orElseThrow(Ausente::new).atraccion())
                .distinct().sorted().toList());
        if (plan != null) datos.bloquearPlan(plan);
        datos.bloquearBoletas(datos.deReserva(id).stream().map(Entrada::id).sorted().toList());
        return datos.deReserva(id);
    }
    private static List<Long> vendidas(List<Entrada> entradas) {
        return entradas.stream().filter(e -> e.estado() == Estado.VENDIDA).map(Entrada::id).sorted().toList();
    }
    private static void exigirOperador(Actor actor) {
        if (actor.rol() == Rol.CLIENTE) throw new Denegado();
    }
    private static void autorizar(Actor actor, long prestador) {
        if (actor.rol() == Rol.ENCARGADO_PRESTADOR && !Long.valueOf(prestador).equals(actor.prestador())) throw new Ausente();
    }
}



