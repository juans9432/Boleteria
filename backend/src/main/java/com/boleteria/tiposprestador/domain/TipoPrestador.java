package com.boleteria.tiposprestador.domain;

public record TipoPrestador(long id, String nombre) {
    public TipoPrestador {
        if (id < 1) throw new Invalido("El ID debe ser positivo.");
        nombre = validarNombre(nombre);
    }

    public static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) throw new Invalido("El nombre es obligatorio.");
        String limpio = nombre.strip();
        if (limpio.codePointCount(0, limpio.length()) > 60) {
            throw new Invalido("El nombre admite hasta 60 caracteres.");
        }
        return limpio;
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) {
            super(mensaje);
        }
    }
}
