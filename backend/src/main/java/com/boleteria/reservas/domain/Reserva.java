package com.boleteria.reservas.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import com.boleteria.identidad.domain.Rol;

public record Reserva(long id, long perfil, Long plan, Instant creada, Estado estado, List<Linea> lineas) {
    public enum Estado { PENDIENTE, CONFIRMADA, CANCELADA, EXPIRADA }
    public record Actor(long usuario, Rol rol, Long prestador) {}
    public record Seleccion(long zona, long tipo, Long combo, Integer grupo) {
        public Seleccion {
            if (zona < 1 || tipo < 1 || (combo != null && combo < 1) || (grupo != null && (grupo < 1 || grupo > 99999999))) {
                throw new Invalida("Selección de entrada inválida.");
            }
        }
    }
    public record Linea(Seleccion seleccion, BigDecimal precio, boolean emitida, String codigo, String estadoBoleta) {}
    public record Solicitud(Long perfil, Long plan, List<Seleccion> selecciones) {
        public Solicitud {
            if (perfil != null && perfil < 1 || plan != null && plan < 1 || selecciones == null
                    || selecciones.isEmpty() || selecciones.size() > 100
                    || selecciones.stream().anyMatch(s -> s == null || (s.combo() == null) != (s.grupo() == null))) {
                throw new Invalida("Selecciona entre 1 y 100 entradas y referencias válidas.");
            }
            selecciones = List.copyOf(selecciones);
        }
    }
    public Instant vence() { return creada.plus(Duration.ofMinutes(15)); }
    public Estado vigente(Instant ahora) {
        return estado == Estado.PENDIENTE && lineas.stream().noneMatch(Linea::emitida) && !ahora.isBefore(vence()) ? Estado.EXPIRADA : estado;
    }
    public void editable(Instant ahora) {
        if (vigente(ahora) != Estado.PENDIENTE || lineas.stream().anyMatch(Linea::emitida)) {
            throw new Conflicto("La reserva ya no es una pendiente editable.");
        }
    }
    public static class Invalida extends RuntimeException { public Invalida(String mensaje) { super(mensaje); } }
    public static class Conflicto extends RuntimeException { public Conflicto(String mensaje) { super(mensaje); } }
    public static class Ausente extends RuntimeException {}
}


