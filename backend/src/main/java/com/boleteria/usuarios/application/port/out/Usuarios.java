package com.boleteria.usuarios.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.usuarios.domain.Usuario;

public interface Usuarios {
    List<String> roles();
    List<Usuario> listar();
    Optional<Usuario> consultar(long id);
    void bloquearAdministracion();
    void bloquearCuenta(long id);
    long administradoresUtilizables();
    boolean prestadorExiste(long id);
    boolean tienePerfil(long id);
    long insertar(Usuario.Datos datos, String hash);
    void actualizar(long id, Usuario.Datos datos, String hash);
    class Fallo extends RuntimeException {
        public Fallo(Throwable causa) { super(causa); }
    }
}


