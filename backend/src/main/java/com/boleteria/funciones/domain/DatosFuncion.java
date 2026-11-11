package com.boleteria.funciones.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public record DatosFuncion(long idAtraccion, LocalDate fecha, LocalTime apertura, LocalTime cierre) {
    public static DatosFuncion programar(Long atraccion, String fecha, String apertura, String cierre, String estado) {
        if (atraccion == null || atraccion < 1) throw new Invalida("Selecciona una atracción válida.");
        if (!"PROGRAMADA".equals(estado)) throw new Invalida("Solo se permite guardar el estado PROGRAMADA.");
        if (fecha == null || !fecha.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new Invalida("La fecha debe tener formato AAAA-MM-DD.");
        }
        try {
            return new DatosFuncion(atraccion, LocalDate.parse(fecha), hora(apertura), hora(cierre));
        } catch (DateTimeParseException error) {
            throw new Invalida("La fecha o la hora no es válida.");
        }
    }

    public DatosFuncion {
        if (idAtraccion < 1 || fecha == null || fecha.getYear() < 1 || apertura == null || cierre == null
                || !apertura.isBefore(cierre)) {
            throw new Invalida("La fecha debe ser válida y la apertura anterior al cierre.");
        }
    }

    private static LocalTime hora(String valor) {
        if (valor == null || !valor.matches("[0-9]{2}:[0-9]{2}")) {
            throw new Invalida("Las horas deben tener formato HH:mm.");
        }
        return LocalTime.parse(valor);
    }

    public static class Invalida extends RuntimeException {
        public Invalida(String mensaje) { super(mensaje); }
    }
}
