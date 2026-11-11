package com.boleteria.ventas.application.usecase;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import com.boleteria.reservas.application.port.out.Reservas;
import com.boleteria.reservas.application.port.out.Reservas.Oferta;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.*;
import com.boleteria.ventas.application.port.in.ConfirmarVentas;
import com.boleteria.ventas.application.port.out.Emision;
import com.boleteria.ventas.application.port.out.Emision.Recurso;
import com.boleteria.ventas.domain.Venta;
import com.boleteria.ventas.domain.Venta.*;

public class ConfirmarVentasCasoDeUso implements ConfirmarVentas {
    private final Reservas reservas;
    private final Emision emision;
    private final Clock reloj;
    private final com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public ConfirmarVentasCasoDeUso(Reservas reservas, Emision emision, Clock reloj, com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos) {
        this.reservas = reservas;
        this.emision = emision;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    public Cotizacion cotizar(Actor actor, long id) {
        var reserva = consultar(actor, id, false);
        reserva.editable(reloj.instant());
        return calcular(reserva, ofertas(reserva));
    }

    public List<Boleta> confirmar(Actor actor, long id, String revision) {
        var reserva = consultar(actor, id, true);
        if (reserva.estado() == Estado.CONFIRMADA) {
            var existentes = emision.emitidas(id);
            if (existentes.isEmpty()) throw new Conflicto("La compra histórica no tiene emisión vinculada.");
            return existentes;
        }
        reserva.editable(reloj.instant());
        var inicial = ofertas(reserva);
        emision.bloquear(Recurso.FUNCION, ids(inicial.values().stream().map(Oferta::funcion)));
        var bajoFuncion = ofertas(reserva);
        emision.bloquear(Recurso.ATRACCION, ids(bajoFuncion.values().stream().map(Oferta::atraccion)));
        emision.bloquear(Recurso.ZONA, ids(inicial.keySet().stream()));
        if (reserva.plan() != null) emision.bloquear(Recurso.PLAN, List.of(reserva.plan()));
        emision.bloquear(Recurso.COMBO, ids(reserva.lineas().stream().map(l -> l.seleccion().combo())));
        emision.bloquear(Recurso.TIPO, ids(reserva.lineas().stream().map(l -> l.seleccion().tipo())));
        if (!reservas.visible(actor, id)) throw new Ausente();

        var cotizacion = calcular(reserva, ofertas(reserva));
        if (!cotizacion.revision().equals(revision)) throw new Recotizar(cotizacion);
        Map<Long, Integer> porZona = new HashMap<>(), porFuncion = new HashMap<>();
        for (var linea : cotizacion.lineas()) {
            porZona.merge(linea.zona(), 1, Integer::sum);
            porFuncion.merge(linea.funcion(), 1, Integer::sum);
        }
        porZona.forEach((zona, cantidad) -> {
            if (cantidad > emision.disponibles(zona)) throw new Conflicto("No hay aforo disponible en la zona " + zona + ".");
        });
        porFuncion.forEach((funcion, cantidad) -> {
            if (cantidad + emision.activas(reserva.perfil(), funcion) > 6) {
                throw new Conflicto("El perfil supera seis boletas activas para la función " + funcion + ".");
            }
        });
        reserva.editable(reloj.instant());
        var emitidas = emision.emitir(id, cotizacion.lineas());
        reserva.editable(reloj.instant());
        reservas.cambiarEstado(id, Estado.CONFIRMADA);
        avisos.publicar(com.boleteria.notificaciones.domain.Aviso.Evento.confirmada(id, emitidas.size()));
        return emitidas;
    }

    private Reserva consultar(Actor actor, long id, boolean bloqueo) {
        if (!reservas.visible(actor, id)) throw new Ausente();
        var reserva = reservas.consultar(id, bloqueo).orElseThrow(Ausente::new);
        if (bloqueo && !reservas.visible(actor, id)) throw new Ausente();
        return reserva;
    }

    private Map<Long, Oferta> ofertas(Reserva reserva) {
        Map<Long, Oferta> resultado = new HashMap<>();
        for (var linea : reserva.lineas()) {
            resultado.computeIfAbsent(linea.seleccion().zona(), zona -> reservas.oferta(zona)
                    .orElseThrow(() -> new Conflicto("Una zona de la selección ya no está disponible.")));
        }
        return resultado;
    }

    private Cotizacion calcular(Reserva reserva, Map<Long, Oferta> ofertas) {
        if (ofertas.isEmpty()) throw new Conflicto("La reserva no contiene entradas.");
        var ahora = LocalDateTime.ofInstant(reloj.instant(), BOGOTA);
        var plan = reserva.plan() == null ? null : reservas.plan(reserva.plan()).filter(Reservas.Plan::activo)
                .orElseThrow(() -> new Conflicto("El plan ya no está activo."));
        Map<Long, Reservas.Combo> combos = new HashMap<>();
        Map<Integer, Long> grupos = new HashMap<>();
        Map<Integer, Set<Long>> atracciones = new HashMap<>();
        Set<Long> menores = new HashSet<>(), adultos = new HashSet<>();
        List<Venta.Linea> lineas = new ArrayList<>();
        var numeros = emision.numeros(reserva.id());
        if (numeros.size() != reserva.lineas().size()) throw new Conflicto("La selección cambió.");
        for (var entrada : reserva.lineas()) {
            var s = entrada.seleccion();
            var oferta = ofertas.get(s.zona());
            if (!oferta.programada() || !ahora.isBefore(oferta.cierre())
                    || plan != null && plan.prestador() != oferta.prestador()) {
                throw new Conflicto("La función o el plan ya no admite esta selección.");
            }
            var tipo = emision.tipo(s.tipo()).orElseThrow(() -> new Conflicto("El tipo de entrada no existe."));
            if (tipo.requiereAdulto()) menores.add(oferta.funcion());
            if (tipo.adulto()) adultos.add(oferta.funcion());
            BigDecimal base = oferta.precio();
            if (s.combo() != null) {
                var combo = combos.computeIfAbsent(s.combo(), id -> reservas.combo(id).filter(Reservas.Combo::activo)
                        .orElseThrow(() -> new Conflicto("El combo ya no está activo.")));
                var anterior = grupos.putIfAbsent(s.grupo(), s.combo());
                var porcentaje = combo.porcentajes().get(oferta.atraccion());
                if (anterior != null && !anterior.equals(s.combo()) || porcentaje == null
                        || !atracciones.computeIfAbsent(s.grupo(), g -> new HashSet<>()).add(oferta.atraccion())
                        || !Venta.enVigencia(oferta.cierre().toLocalDate(), ahora.toLocalDate(), combo.vigencia())) {
                    throw new Conflicto("La composición o la vigencia del combo cambió.");
                }
                base = combo.precio().multiply(porcentaje).movePointLeft(2);
            }
            lineas.add(new Venta.Linea(numeros.get(lineas.size()), s.zona(), oferta.funcion(), s.tipo(), s.combo(), s.grupo(), Venta.precio(base, tipo)));
        }
        grupos.forEach((grupo, combo) -> {
            var porcentajes = combos.get(combo).porcentajes();
            if (!atracciones.get(grupo).equals(porcentajes.keySet())
                    || porcentajes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.valueOf(100)) != 0) {
                throw new Conflicto("Cada unidad debe incluir todas las atracciones y su reparto debe sumar 100 %.");
            }
        });
        if (!adultos.containsAll(menores)) throw new Conflicto("Se requiere una entrada ADULTO en la misma reserva y función.");
        var total = lineas.stream().map(Venta.Linea::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Cotizacion(revision(reserva.id() + ":" + reserva.perfil() + ":" + reserva.plan() + ":" + lineas),
                List.copyOf(lineas), total);
    }

    private static List<Long> ids(java.util.stream.Stream<Long> ids) {
        return ids.filter(Objects::nonNull).distinct().sorted().toList();
    }
    private static String revision(String datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}



