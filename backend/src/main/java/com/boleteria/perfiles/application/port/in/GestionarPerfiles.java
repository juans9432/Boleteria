package com.boleteria.perfiles.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.perfiles.domain.Perfil;

public interface GestionarPerfiles {
    List<Perfil> listar(Acceso acceso);
    Optional<Perfil> consultar(Acceso acceso, long id);
    Perfil crear(Acceso acceso, Perfil.Datos datos);
    Optional<Perfil> actualizar(Acceso acceso, long id, Perfil.Datos datos);
    boolean eliminar(Acceso acceso, long id);
    List<CuentaVinculable> cuentasVinculables(Acceso acceso);

    record Acceso(long idUsuario, Rol rol) {}
    enum Rol { ADMINISTRADOR, OPERADOR, CLIENTE }
    record CuentaVinculable(long idUsuario, String correo) {}
    class Denegado extends RuntimeException {}
}
