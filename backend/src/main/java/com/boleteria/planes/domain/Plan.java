package com.boleteria.planes.domain;

public record Plan(long id, String nombre, String descripcion, String estado, long idPrestador) {
}
