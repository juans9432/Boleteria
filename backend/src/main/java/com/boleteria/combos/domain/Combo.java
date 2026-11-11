package com.boleteria.combos.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

public record Combo(long id, Datos datos) {
    public enum Estado { ACTIVO, INACTIVO }

    public record Atraccion(long idAtraccion, BigDecimal porcentaje) {
        public Atraccion {
            if (idAtraccion < 1) throw new Invalido("Selecciona una atracción válida.");
            porcentaje = decimal(porcentaje, "1", "100", "porcentaje");
        }
    }

    public record Datos(String nombre, BigDecimal precio, int diasVigencia, Estado estado, List<Atraccion> atracciones) {
        public Datos {
            if (nombre == null || nombre.isBlank()) throw new Invalido("El nombre es obligatorio.");
            nombre = nombre.strip();
            if (nombre.codePointCount(0, nombre.length()) > 100) throw new Invalido("El nombre admite hasta 100 caracteres.");
            precio = decimal(precio, "0.01", "99999999.99", "precio");
            if (diasVigencia < 1 || diasVigencia > 999) throw new Invalido("La vigencia debe estar entre 1 y 999 días.");
            if (estado == null) throw new Invalido("Indica un estado válido.");
            if (atracciones == null || atracciones.stream().anyMatch(java.util.Objects::isNull)) {
                throw new Invalido("Indica las atracciones del combo.");
            }
            var ids = new HashSet<Long>();
            for (var atraccion : atracciones) {
                if (!ids.add(atraccion.idAtraccion())) throw new Invalido("Una atracción no puede repetirse en el combo.");
            }
            atracciones = atracciones.stream().sorted(java.util.Comparator.comparingLong(Atraccion::idAtraccion)).toList();
        }

        public void verificarActivacion() {
            if (estado != Estado.ACTIVO) return;
            BigDecimal total = atracciones.stream().map(Atraccion::porcentaje).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(new BigDecimal("100")) != 0) {
                throw new Invalido("Para activar el combo, los porcentajes deben sumar exactamente 100 %.");
            }
        }
    }

    private static BigDecimal decimal(BigDecimal valor, String minimo, String maximo, String campo) {
        if (valor == null || valor.compareTo(new BigDecimal(minimo)) < 0
                || valor.compareTo(new BigDecimal(maximo)) > 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new Invalido("Revisa el rango y los decimales del " + campo + ".");
        }
        return valor.setScale(2);
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }

    public static class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) { super(mensaje); }
    }
}
