package com.boleteria.tiposentrada.domain;

import java.math.BigDecimal;

public record TipoEntrada(long id, Datos datos) {
    public record Datos(String nombre, BigDecimal porcentajeDescuento, String requiereAdulto) {
        public Datos {
            if (nombre == null || nombre.isBlank()) throw new Invalido("El nombre es obligatorio.");
            nombre = nombre.strip();
            if (nombre.codePointCount(0, nombre.length()) > 40) throw new Invalido("El nombre admite hasta 40 caracteres.");
            if (porcentajeDescuento == null || porcentajeDescuento.signum() < 0
                    || porcentajeDescuento.compareTo(BigDecimal.valueOf(100)) > 0
                    || porcentajeDescuento.stripTrailingZeros().scale() > 2) {
                throw new Invalido("El descuento debe estar entre 0 y 100 y admitir hasta dos decimales.");
            }
            if (!"S".equals(requiereAdulto) && !"N".equals(requiereAdulto)) {
                throw new Invalido("Requiere adulto debe ser S o N.");
            }
            porcentajeDescuento = porcentajeDescuento.setScale(2);
        }
    }

    public static class SemanticaHistorica extends RuntimeException {}
    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }
}

