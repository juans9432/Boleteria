package com.boleteria.atracciones.domain;

public record DatosAtraccion(String nombre, String descripcion, Long idPrestador) {
    public DatosAtraccion {
        if (nombre == null || nombre.isBlank()) throw new Invalido("El nombre es obligatorio.");
        nombre = nombre.strip();
        descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.strip();
        if (nombre.codePointCount(0, nombre.length()) > 120
                || (descripcion != null && descripcion.codePointCount(0, descripcion.length()) > 500)) {
            throw new Invalido("El nombre admite hasta 120 caracteres y la descripción hasta 500.");
        }
        if (idPrestador == null || idPrestador < 1) throw new Invalido("Selecciona un prestador válido.");
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }
}
