package com.boleteria.usuarios.application.usecase;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import com.boleteria.identidad.application.port.out.CodificarClave;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.usuarios.application.port.in.AdministrarUsuarios;
import com.boleteria.usuarios.application.port.out.Usuarios;
import com.boleteria.usuarios.domain.Usuario;

public class AdministrarUsuariosCasoDeUso implements AdministrarUsuarios {
    private final Usuarios usuarios;
    private final CodificarClave codificador;

    public AdministrarUsuariosCasoDeUso(Usuarios usuarios, CodificarClave codificador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
    }

    @Override
    public List<String> roles() { return usuarios.roles(); }

    @Override
    public List<Usuario> listar() { return usuarios.listar(); }

    @Override
    public Optional<Usuario> consultar(long id) { return usuarios.consultar(id); }

    @Override
    public Usuario crear(long administrador, Usuario.Datos datos, String clave) {
        String hash = codificar(clave);
        usuarios.bloquearAdministracion();
        verificarAdministrador(administrador);
        verificarPrestador(datos);
        return resultado(usuarios.insertar(datos, hash), datos, true);
    }

    @Override
    public Optional<Usuario> actualizar(long administrador, long id, Usuario.Datos datos, String clave) {
        String hash = clave == null ? null : codificar(clave);
        usuarios.bloquearAdministracion();
        verificarAdministrador(administrador);
        usuarios.bloquearCuenta(id);
        return usuarios.consultar(id).map(actual -> {
            verificarPrestador(datos);
            if (datos.rol() != Rol.CLIENTE && usuarios.tienePerfil(id)) {
                throw new Usuario.Conflicto("La cuenta tiene un perfil vinculado y debe conservar el rol CLIENTE.");
            }
            boolean utilizable = hash != null || actual.credencialUtilizable();
            boolean pierdeAdministrador = esAdministrador(actual)
                    && !(datos.rol() == Rol.ADMINISTRADOR && datos.activo() && utilizable);
            if (pierdeAdministrador && usuarios.administradoresUtilizables() <= 1) {
                throw new Usuario.Conflicto("Debe quedar al menos un administrador activo con credenciales utilizables.");
            }
            usuarios.actualizar(id, datos, hash);
            return resultado(id, datos, utilizable);
        });
    }

    private void verificarAdministrador(long id) {
        if (usuarios.consultar(id).filter(AdministrarUsuariosCasoDeUso::esAdministrador).isEmpty()) {
            throw new Usuario.Denegado();
        }
    }

    private static boolean esAdministrador(Usuario usuario) {
        return usuario.rol() == Rol.ADMINISTRADOR && usuario.activo() && usuario.credencialUtilizable()
                && usuario.idPrestador() == null;
    }

    private void verificarPrestador(Usuario.Datos datos) {
        if (datos.idPrestador() != null && !usuarios.prestadorExiste(datos.idPrestador())) {
            throw new Usuario.Invalido("El prestador seleccionado no existe.");
        }
    }

    private String codificar(String clave) {
        if (clave == null || clave.isBlank() || clave.length() < 12
                || clave.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new Usuario.Invalido("La contraseña debe tener al menos 12 caracteres y hasta 72 bytes UTF-8.");
        }
        return codificador.codificar(clave);
    }

    private static Usuario resultado(long id, Usuario.Datos datos, boolean utilizable) {
        return new Usuario(id, datos.correo(), datos.rol(), datos.activo(), datos.idPrestador(), utilizable);
    }
}


