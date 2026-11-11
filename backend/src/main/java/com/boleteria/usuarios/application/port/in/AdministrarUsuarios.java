package com.boleteria.usuarios.application.port.in;

import java.util.List;
import java.util.Optional;
import com.boleteria.usuarios.domain.Usuario;

public interface AdministrarUsuarios {
    List<String> roles();
    List<Usuario> listar();
    Optional<Usuario> consultar(long id);
    Usuario crear(long administrador, Usuario.Datos datos, String clave);
    Optional<Usuario> actualizar(long administrador, long id, Usuario.Datos datos, String clave);
}

