package com.boleteria.boletas.domain;

import java.math.BigDecimal;
import java.time.*;

public record Boleta(long id, String codigo, Estado estado, BigDecimal precioPagado, long reserva,
                    long zona, String nombreZona, long tipo, String nombreTipo, Integer lineaReserva,
                    long funcion, String atraccion, LocalDate fecha, LocalTime apertura, LocalTime cierre,
                    String estadoFuncion, Long combo, Integer grupoCombo) {
    public enum Estado { VENDIDA, USADA, ANULADA }
    public void validarUso(Instant instante) {
        if (estado != Estado.VENDIDA) throw new Conflicto("Solo se puede usar una boleta VENDIDA.");
        if (!"PROGRAMADA".equals(estadoFuncion)) throw new Conflicto("La función debe estar PROGRAMADA.");
        var ahora = instante.atZone(ZoneId.of("America/Bogota"));
        if (!fecha.equals(ahora.toLocalDate()) || ahora.toLocalTime().isBefore(apertura)
                || !ahora.toLocalTime().isBefore(cierre)) {
            throw new Conflicto("La boleta solo se puede usar durante la función, en hora de Colombia.");
        }
    }
    public static class Conflicto extends RuntimeException { public Conflicto(String mensaje) { super(mensaje); } }
    public static class Ausente extends RuntimeException {}
    public static class Denegado extends RuntimeException {}
}


