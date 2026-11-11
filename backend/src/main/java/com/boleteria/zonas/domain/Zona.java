package com.boleteria.zonas.domain;

import java.math.BigDecimal;

public record Zona(long id, long idFuncion, String nombre, int aforo, BigDecimal precio) {
    public record Datos(String nombre, Integer aforo, BigDecimal precio) {
        public Datos {
            if (nombre == null || nombre.isBlank()) throw new Invalida("El nombre de la zona es obligatorio.");
            nombre = nombre.strip();
            if (nombre.codePointCount(0, nombre.length()) > 40) throw new Invalida("El nombre admite hasta 40 caracteres.");
            if (aforo == null || aforo < 1 || aforo > 999999) throw new Invalida("El aforo debe estar entre 1 y 999999.");
            if (precio == null || precio.signum() <= 0 || precio.compareTo(new BigDecimal("99999999.99")) > 0
                    || precio.stripTrailingZeros().scale() > 2) {
                throw new Invalida("El precio debe ser positivo y admitir hasta ocho enteros y dos decimales.");
            }
        }

        public static Datos desde(String nombre, BigDecimal aforo, BigDecimal precio) {
            try {
                return new Datos(nombre, aforo == null ? null : aforo.intValueExact(), precio);
            } catch (ArithmeticException error) {
                throw new Invalida("El aforo debe ser un número entero entre 1 y 999999.");
            }
        }

        public void verificarOcupacion(long ocupadas) {
            if (aforo < ocupadas) throw new Conflicto("El aforo no puede ser inferior a " + ocupadas + " cupos ocupados.");
        }
    }

    public static class Invalida extends RuntimeException {
        public Invalida(String mensaje) { super(mensaje); }
    }

    public static class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) { super(mensaje); }
    }
}

