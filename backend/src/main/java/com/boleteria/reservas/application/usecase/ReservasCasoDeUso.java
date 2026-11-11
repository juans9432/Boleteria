package com.boleteria.reservas.application.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.reservas.application.port.in.GestionarReservas;
import com.boleteria.reservas.application.port.out.Reservas;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.*;

public class ReservasCasoDeUso implements GestionarReservas {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private final Reservas datos;
    private final Clock reloj;
    private final com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos;

    public ReservasCasoDeUso(Reservas datos, Clock reloj, com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos) {
        this.datos = datos;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    public Reservas.Catalogos catalogos(Actor actor) { return datos.catalogos(actor); }

    public List<Reserva> listar(Actor actor) {
        return datos.ids(actor).stream().filter(id -> datos.visible(actor, id))
                .map(id -> datos.consultar(id, false).orElseThrow(Ausente::new)).map(this::estadoVigente).toList();
    }
    public Reserva consultar(Actor actor, long id) { return estadoVigente(accesible(actor, id, false)); }

    public Reserva crear(Actor actor, Solicitud solicitud) {
        long perfil = perfil(actor, solicitud);
        List<Linea> lineas = cotizar(actor, solicitud);
        long id = datos.insertar(perfil, solicitud.plan(), reloj.instant());
        datos.reemplazarLineas(id, lineas);
        return accesible(actor, id, false);
    }
    public Reserva editar(Actor actor, long id, Solicitud solicitud) {
        Reserva actual = accesible(actor, id, true);
        actual.editable(reloj.instant());
        long perfil = perfil(actor, solicitud);
        List<Linea> lineas = cotizar(actor, solicitud);
        actual.editable(reloj.instant());
        datos.actualizar(id, perfil, solicitud.plan());
        datos.reemplazarLineas(id, lineas);
        return accesible(actor, id, false);
    }
    public Reserva cancelar(Actor actor, long id) {
        Reserva actual = accesible(actor, id, true);
        actual.editable(reloj.instant());
        datos.cambiarEstado(id, Estado.CANCELADA);
        avisos.publicar(com.boleteria.notificaciones.domain.Aviso.Evento.cancelada(id, 0));
        return accesible(actor, id, false);
    }
    public int expirar() {
        Instant ahora = reloj.instant();
        var ids = datos.vencidas(ahora.minusSeconds(900));
        ids.forEach(id -> {
            datos.cambiarEstado(id, Estado.EXPIRADA);
            avisos.publicar(com.boleteria.notificaciones.domain.Aviso.Evento.expirada(id));
        });
        return ids.size();
    }

    private Reserva estadoVigente(Reserva r) {
        return new Reserva(r.id(), r.perfil(), r.plan(), r.creada(), r.vigente(reloj.instant()), r.lineas());
    }

    private Reserva accesible(Actor actor, long id, boolean bloqueo) {
        if (id < 1 || !datos.visible(actor, id)) throw new Ausente();
        Reserva reserva = datos.consultar(id, bloqueo).orElseThrow(Ausente::new);
        if (bloqueo && !datos.visible(actor, id)) throw new Ausente();
        return reserva;
    }

    private long perfil(Actor actor, Solicitud solicitud) {
        if (actor.rol() == Rol.CLIENTE) {
            long propio = datos.perfilDe(actor.usuario());
            if (propio == 0 || solicitud.perfil() != null && solicitud.perfil() != propio) throw new Ausente();
            return propio;
        }
        if (solicitud.perfil() == null || !datos.existePerfil(solicitud.perfil())) {
            throw new Invalida("Selecciona un perfil existente.");
        }
        return solicitud.perfil();
    }

    private List<Linea> cotizar(Actor actor, Solicitud solicitud) {
        Long prestadorPlan = solicitud.plan() == null ? null : datos.plan(solicitud.plan())
                .filter(Reservas.Plan::activo).orElseThrow(() -> new Invalida("El plan debe estar activo.")).prestador();
        LocalDateTime ahora = LocalDateTime.ofInstant(reloj.instant(), BOGOTA);
        Map<Integer, Long> combos = new HashMap<>();
        Map<Integer, Set<Long>> atracciones = new HashMap<>();
        Map<Long, Reservas.Combo> catalogoCombos = new HashMap<>();
        List<Linea> lineas = new ArrayList<>();
        for (var seleccion : solicitud.selecciones()) {
            var oferta = datos.oferta(seleccion.zona()).orElseThrow(() -> new Invalida("Zona inexistente."));
            if (actor.rol() == Rol.ENCARGADO_PRESTADOR && !Long.valueOf(oferta.prestador()).equals(actor.prestador())) {
                throw new Ausente();
            }
            if (!oferta.programada() || !ahora.isBefore(oferta.cierre())) {
                throw new Conflicto("La función debe estar programada y seguir disponible.");
            }
            if (prestadorPlan != null && prestadorPlan != oferta.prestador()) {
                throw new Invalida("Todas las selecciones deben pertenecer al prestador del plan.");
            }
            BigDecimal base = oferta.precio();
            if (seleccion.combo() != null) {
                long comboId = seleccion.combo();
                var anterior = combos.putIfAbsent(seleccion.grupo(), comboId);
                var combo = catalogoCombos.computeIfAbsent(comboId, id -> datos.combo(id)
                        .filter(Reservas.Combo::activo).orElseThrow(() -> new Invalida("El combo debe estar activo.")));
                var porcentaje = combo.porcentajes().get(oferta.atraccion());
                if (anterior != null && anterior != comboId || porcentaje == null
                        || !atracciones.computeIfAbsent(seleccion.grupo(), g -> new HashSet<>()).add(oferta.atraccion())
                        || !oferta.cierre().toLocalDate().isBefore(ahora.toLocalDate().plusDays(combo.vigencia()))) {
                    throw new Invalida("La selección no corresponde a la composición o vigencia del combo.");
                }
                base = combo.precio().multiply(porcentaje).movePointLeft(2);
            }
            var descuento = datos.descuento(seleccion.tipo()).orElseThrow(() -> new Invalida("Tipo de entrada inexistente."));
            var precio = base.multiply(CIEN.subtract(descuento)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            lineas.add(new Linea(seleccion, precio, false, null, null));
        }
        combos.forEach((grupo, id) -> {
            var porcentajes = catalogoCombos.get(id).porcentajes();
            if (!porcentajes.keySet().equals(atracciones.get(grupo))
                    || porcentajes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(CIEN) != 0) {
                throw new Invalida("Cada unidad del combo debe incluir todas sus atracciones y sumar 100 %.");
            }
        });
        return lineas;
    }
}





