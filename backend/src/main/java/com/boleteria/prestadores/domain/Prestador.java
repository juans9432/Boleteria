package com.boleteria.prestadores.domain;

public record Prestador(long id, String nit, String nombre, String correo, long idMunicipio, long idTipoPrestador) {
}
