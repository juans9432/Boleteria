package com.boleteria.perfiles.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.CuentaVinculable;
import com.boleteria.perfiles.domain.Perfil;

public interface Perfiles {
    List<Perfil> listarTodos();
    List<Perfil> listarMostrador();
    Optional<Perfil> buscarPorUsuario(long idUsuario);
    Optional<Perfil> consultar(long id, boolean bloquear);
    Optional<Cuenta> bloquearCuenta(long idUsuario);
    List<CuentaVinculable> cuentasVinculables();
    long insertar(Perfil.Datos datos);
    void actualizar(long id, Perfil.Datos datos);
    void bloquearEscriturasDeReservas();
    boolean tieneReservas(long id);
    boolean eliminar(long id);

    record Cuenta(long id, String correo, String rol, String estado) {}
    class Fallo extends RuntimeException {
        public Fallo(Throwable causa) { super(causa); }
    }
}

