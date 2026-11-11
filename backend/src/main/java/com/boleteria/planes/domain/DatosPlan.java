package com.boleteria.planes.domain;

public record DatosPlan(String nombre, String descripcion, String estado, Long idPrestador) {
    public DatosPlan {
        if (nombre == null || nombre.isBlank()) throw new Invalido("El nombre es obligatorio.");
        nombre = nombre.strip();
        descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.strip();
        if (nombre.codePointCount(0, nombre.length()) > 100
                || (descripcion != null && descripcion.codePointCount(0, descripcion.length()) > 500)) {
            throw new Invalido("El nombre admite hasta 100 caracteres y la descripción hasta 500.");
        }
        if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) {
            throw new Invalido("El estado debe ser ACTIVO o INACTIVO.");
        }
        if (idPrestador == null || idPrestador < 1) throw new Invalido("Selecciona un prestador válido.");
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }
}
