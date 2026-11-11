package com.boleteria.ventas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public final class Venta {
    private Venta() {}
    public record Tipo(BigDecimal descuento, boolean requiereAdulto, boolean adulto) {}
    public record Linea(int numero, long zona, long funcion, long tipo, Long combo, Integer grupo, BigDecimal precio) {}
    public record Cotizacion(String revision, List<Linea> lineas, BigDecimal total) {}
    public record Boleta(String codigo, int linea, BigDecimal precio) {
        public Boleta { precio = precio.setScale(2); }
    }
    public static BigDecimal precio(BigDecimal base, Tipo tipo) {
        return base.multiply(BigDecimal.valueOf(100).subtract(tipo.descuento())).movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP);
    }
    public static boolean enVigencia(LocalDate funcion, LocalDate venta, int dias) {
        return !funcion.isBefore(venta) && funcion.isBefore(venta.plusDays(dias));
    }
    public static class Recotizar extends RuntimeException {
        private final Cotizacion cotizacion;
        public Recotizar(Cotizacion cotizacion) { this.cotizacion = cotizacion; }
        public Cotizacion cotizacion() { return cotizacion; }
    }
}


