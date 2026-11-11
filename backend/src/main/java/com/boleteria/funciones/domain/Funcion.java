package com.boleteria.funciones.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record Funcion(long id, long idAtraccion, LocalDate fecha, LocalTime apertura,
                      LocalTime cierre, Estado estado) {
    public enum Estado { PROGRAMADA, CANCELADA, REALIZADA }

    public boolean cruza(DatosFuncion datos) {
        return estado != Estado.CANCELADA && apertura.isBefore(datos.cierre())
                && cierre.isAfter(datos.apertura());
    }
}
