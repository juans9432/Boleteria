package com.boleteria.municipios.domain;

public record DatosMunicipio(String nombre, String departamento) {
    public DatosMunicipio {
        nombre = validar(nombre, 80, "nombre");
        departamento = validar(departamento, 60, "departamento");
    }

    private static String validar(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new Invalido("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.strip();
        if (limpio.codePointCount(0, limpio.length()) > maximo) {
            throw new Invalido("El campo " + campo + " admite hasta " + maximo + " caracteres.");
        }
        return limpio;
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) {
            super(mensaje);
        }
    }
}
