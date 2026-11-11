package com.boleteria.usuarios.domain;

import com.boleteria.identidad.domain.Rol;

public record Usuario(long id, String correo, Rol rol, boolean activo, Long idPrestador, boolean credencialUtilizable) {
    public record Datos(String correo, Rol rol, boolean activo, Long idPrestador) {
        public Datos {
            correo = correo == null ? "" : correo.strip();
            if (correo.length() > 100 || !correo.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                throw new Invalido("Indica un correo válido de hasta 100 caracteres.");
            }
            if (rol == null || (rol == Rol.ENCARGADO_PRESTADOR
                    ? idPrestador == null || idPrestador < 1 : idPrestador != null)) {
                throw new Invalido("Solo el encargado debe tener un prestador asignado.");
            }
        }
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }
    public static class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) { super(mensaje); }
    }
    public static class Denegado extends RuntimeException {}
}
