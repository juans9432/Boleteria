package com.boleteria.reservas.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.Actor;
import com.boleteria.reservas.domain.Reserva.Linea;

public interface Reservas {
    record Plan(long prestador, boolean activo) {}
    record Oferta(long funcion, long atraccion, long prestador, BigDecimal precio,
                  boolean programada, LocalDateTime cierre) {}
    record Combo(BigDecimal precio, Map<Long, BigDecimal> porcentajes, int vigencia, boolean activo) {}
    record Opcion(long id, String nombre) {}
    record Catalogos(List<Opcion> perfiles, List<Opcion> planes, List<Opcion> zonas,
                     List<Opcion> tipos, List<Opcion> combos) {}
    Catalogos catalogos(Actor actor);
    List<Long> ids(Actor actor);
    Optional<Reserva> consultar(long id, boolean bloquear);
    boolean visible(Actor actor, long id);
    long perfilDe(long usuario);
    boolean existePerfil(long id);
    Optional<Plan> plan(long id);
    Optional<Oferta> oferta(long zona);
    Optional<BigDecimal> descuento(long tipo);
    Optional<Combo> combo(long id);
    long insertar(long perfil, Long plan, Instant fecha);
    void actualizar(long id, long perfil, Long plan);
    void reemplazarLineas(long id, List<Linea> lineas);
    void cambiarEstado(long id, Reserva.Estado estado);
    List<Long> vencidas(Instant limite);
}


