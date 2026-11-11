package com.boleteria.notificaciones.domain;

import java.time.Instant;
import java.util.List;

public record Aviso(long id, Long reserva, String asunto, String mensaje, Instant fecha, boolean leida) {
    public record Evento(long reserva, String operacion, String asunto, String mensaje) {
        public static Evento confirmada(long reserva, int boletas) {
            return new Evento(reserva, "CONFIRMAR", "Reserva confirmada",
                    "La reserva #" + reserva + " fue confirmada. Boletas emitidas: " + boletas + ".");
        }
        public static Evento cancelada(long reserva, int boletas) {
            return new Evento(reserva, "CANCELAR", "Reserva cancelada",
                    "La reserva #" + reserva + " fue cancelada. Boletas anuladas: " + boletas + ".");
        }
        public static Evento expirada(long reserva) {
            return new Evento(reserva, "EXPIRAR", "Reserva expirada",
                    "La reserva #" + reserva + " venció sin confirmación.");
        }
        public static Evento anulacion(long reserva, String operacion, String asunto, List<String> afectadas,
                                      List<String> vigentes) {
            return new Evento(reserva, operacion, asunto, "Reserva #" + reserva + ". Anuladas en esta operación: "
                    + resumen(afectadas) + ". Vigentes al consultar para este aviso: " + resumen(vigentes)
                    + ". Consulta el detalle para ver el estado actual.");
        }
        private static String resumen(List<String> codigos) {
            if (codigos.isEmpty()) return "ninguna";
            return codigos.size() + " (" + String.join(", ", codigos.stream().limit(3).toList())
                    + (codigos.size() > 3 ? ", entre otras" : "") + ")";
        }
    }
    public static class Ausente extends RuntimeException {}
}

