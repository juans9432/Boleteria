package com.boleteria.prestadores.domain;

import java.util.regex.Pattern;

public record DatosPrestador(String nit, String nombre, String correo, Long idMunicipio, Long idTipoPrestador) {
    private static final Pattern CORREO = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    public DatosPrestador {
        nit = requerido(nit, 20, "NIT");
        nombre = requerido(nombre, 120, "nombre");
        correo = correo == null || correo.isBlank() ? null : requerido(correo, 100, "correo");
        if (correo != null && !CORREO.matcher(correo).matches()) {
            throw new Invalido("El correo no tiene un formato válido.");
        }
        if (idMunicipio == null || idMunicipio < 1 || idTipoPrestador == null || idTipoPrestador < 1) {
            throw new Invalido("Selecciona un municipio y un tipo de prestador válidos.");
        }
    }

    private static String requerido(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) throw new Invalido("El campo " + campo + " es obligatorio.");
        String limpio = valor.strip();
        if (limpio.codePointCount(0, limpio.length()) > maximo) {
            throw new Invalido("El campo " + campo + " admite hasta " + maximo + " caracteres.");
        }
        return limpio;
    }

    public static class Invalido extends RuntimeException {
        public Invalido(String mensaje) { super(mensaje); }
    }
}
