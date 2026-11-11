package com.boleteria.perfiles.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles;
import com.boleteria.perfiles.application.port.out.Perfiles;
import com.boleteria.perfiles.domain.Perfil;

public class GestionarPerfilesCasoDeUso implements GestionarPerfiles {
    private final Perfiles perfiles;

    public GestionarPerfilesCasoDeUso(Perfiles perfiles) { this.perfiles = perfiles; }

    @Override
    public List<Perfil> listar(Acceso acceso) {
        return switch (acceso.rol()) {
            case ADMINISTRADOR -> perfiles.listarTodos();
            case OPERADOR -> perfiles.listarMostrador();
            case CLIENTE -> perfiles.buscarPorUsuario(acceso.idUsuario()).stream().toList();
        };
    }

    @Override
    public Optional<Perfil> consultar(Acceso acceso, long id) {
        validarId(id);
        return perfiles.consultar(id, false).map(perfil -> {
            autorizar(acceso, perfil);
            return perfil;
        });
    }

    @Override
    public Perfil crear(Acceso acceso, Perfil.Datos datos) {
        if (acceso.rol() == Rol.CLIENTE || (acceso.rol() == Rol.OPERADOR && datos.idUsuario() != null)) throw new Denegado();
        String correo = verificarCuenta(datos.idUsuario());
        return new Perfil(perfiles.insertar(datos), datos, correo);
    }

    @Override
    public Optional<Perfil> actualizar(Acceso acceso, long id, Perfil.Datos datos) {
        validarId(id);
        return perfiles.consultar(id, true).map(actual -> {
            autorizar(acceso, actual);
            boolean cambiaCuenta = !Objects.equals(actual.datos().idUsuario(), datos.idUsuario());
            boolean cambiaDocumento = !actual.datos().documento().equals(datos.documento());
            if ((acceso.rol() != Rol.ADMINISTRADOR && cambiaCuenta) || (acceso.rol() == Rol.CLIENTE && cambiaDocumento)) {
                throw new Denegado();
            }
            if (cambiaDocumento || (cambiaCuenta && actual.datos().idUsuario() != null)) {
                perfiles.bloquearEscriturasDeReservas();
                if (perfiles.tieneReservas(id)) {
                    throw new Perfil.Conflicto("El perfil tiene reservas. Se debe conservar su documento y la cuenta vinculada.");
                }
            }
            String correo = cambiaCuenta ? verificarCuenta(datos.idUsuario()) : actual.correoCuenta();
            perfiles.actualizar(id, datos);
            return new Perfil(id, datos, correo);
        });
    }

    @Override
    public boolean eliminar(Acceso acceso, long id) {
        exigirAdministrador(acceso);
        validarId(id);
        return perfiles.consultar(id, true).map(actual -> {
            if (perfiles.tieneReservas(id)) throw new Perfil.Conflicto("El perfil tiene reservas asociadas y no se puede eliminar.");
            return perfiles.eliminar(id);
        }).orElse(false);
    }

    @Override
    public List<CuentaVinculable> cuentasVinculables(Acceso acceso) {
        exigirAdministrador(acceso);
        return perfiles.cuentasVinculables();
    }

    private String verificarCuenta(Long idUsuario) {
        if (idUsuario == null) return null;
        return perfiles.bloquearCuenta(idUsuario)
                .filter(c -> "CLIENTE".equals(c.rol()) && "ACTIVO".equals(c.estado()))
                .orElseThrow(() -> new Perfil.Invalido("Selecciona una cuenta activa con rol CLIENTE."))
                .correo();
    }

    private static void autorizar(Acceso acceso, Perfil perfil) {
        Long cuenta = perfil.datos().idUsuario();
        if (acceso.rol() == Rol.OPERADOR && cuenta != null
                || acceso.rol() == Rol.CLIENTE && (cuenta == null || cuenta != acceso.idUsuario())) {
            throw new Denegado();
        }
    }

    private static void exigirAdministrador(Acceso acceso) {
        if (acceso.rol() != Rol.ADMINISTRADOR) throw new Denegado();
    }

    private static void validarId(long id) {
        if (id < 1) throw new Perfil.Invalido("El ID debe ser positivo.");
    }
}

