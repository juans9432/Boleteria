package com.boleteria.perfiles.domain;

public record Perfil(long id, Datos datos, String correoCuenta) {
    public record Datos(String documento, String nombre, String telefono, Long idUsuario) {
        public Datos {
            documento = requerido(documento, 20, "documento");
            nombre = requerido(nombre, 100, "nombre");
            telefono = telefono == null || telefono.isBlank() ? null : requerido(telefono, 20, "teléfono");
            if (telefono != null && (!telefono.matches("[+0-9 ()-]+") || !telefono.matches(".*[0-9].*"))) {
                throw new Invalido("Indica un teléfono con dígitos y separadores válidos.");
            }
            if (idUsuario != null && idUsuario < 1) throw new Invalido("Selecciona una cuenta válida.");
        }

        private static String requerido(String valor, int maximo, String campo) {
            if (valor == null || valor.isBlank()) throw new Invalido("El campo " + campo + " es obligatorio.");
            String limpio = valor.strip();
            if (limpio.codePointCount(0, limpio.length()) > maximo) {
                throw new Invalido("El campo " + campo + " admite hasta " + maximo + " caracteres.");
            }
            return limpio;
        }
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }

    public static class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) { super(mensaje); }
    }
}
