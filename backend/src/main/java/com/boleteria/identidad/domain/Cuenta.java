package com.boleteria.identidad.domain;

public record Cuenta(long id, String correo, Rol rol, boolean activa, Long idPrestador) {
    public boolean habilitada() {
        return activa && rol != null && (rol == Rol.ENCARGADO_PRESTADOR
                ? idPrestador != null && idPrestador > 0 : idPrestador == null);
    }

    public boolean puedeAdministrar(long prestador) {
        return prestador > 0 && habilitada() && (rol == Rol.ADMINISTRADOR
                || (rol == Rol.ENCARGADO_PRESTADOR && idPrestador == prestador));
    }
}
