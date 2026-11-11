package com.boleteria.usuarios.infrastructure.web;

import java.net.URI;
import java.security.Principal;

import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.identidad.infrastructure.security.SesionesVigentes;
import com.boleteria.usuarios.application.port.in.AdministrarUsuarios;
import com.boleteria.usuarios.domain.Usuario;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api")
class UsuariosController {
    private final AdministrarUsuarios usuarios;
    private final ConsultarIdentidad identidad;
    private final SesionesVigentes sesiones;

    UsuariosController(AdministrarUsuarios usuarios, ConsultarIdentidad identidad, SesionesVigentes sesiones) {
        this.usuarios = usuarios;
        this.identidad = identidad;
        this.sesiones = sesiones;
    }

    @GetMapping("/roles")
    List<String> roles() { return usuarios.roles(); }

    @GetMapping("/usuarios")
    List<Respuesta> listar() { return usuarios.listar().stream().map(Respuesta::desde).toList(); }

    @GetMapping("/usuarios/{id}")
    ResponseEntity<Respuesta> consultar(@PathVariable long id) {
        return ResponseEntity.of(usuarios.consultar(id).map(Respuesta::desde));
    }

    @PostMapping("/usuarios")
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var usuario = usuarios.crear(administrador(principal), solicitud.datos(), solicitud.clave());
        return ResponseEntity.created(URI.create("/api/usuarios/" + usuario.id())).body(Respuesta.desde(usuario));
    }

    @PutMapping("/usuarios/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(usuarios.actualizar(administrador(principal), id, solicitud.datos(), solicitud.clave())
                .map(usuario -> {
                    sesiones.revocar(id);
                    return Respuesta.desde(usuario);
                }));
    }

    private long administrador(Principal principal) {
        return identidad.consultarCuenta(principal.getName()).filter(c -> c.rol() == Rol.ADMINISTRADOR)
                .orElseThrow(Usuario.Denegado::new).id();
    }

    record Solicitud(String correo, String rol, String estado, java.math.BigDecimal idPrestador, String clave) {
        Usuario.Datos datos() {
            if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) throw new Usuario.Invalido("Estado inválido.");
            try {
                return new Usuario.Datos(correo, Rol.valueOf(rol == null ? "" : rol),
                        "ACTIVO".equals(estado), idPrestador == null ? null : idPrestador.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw new Usuario.Invalido("Indica un rol y un prestador válidos.");
            }
        }
        @Override public String toString() { return "Solicitud de administración de cuenta"; }
    }

    record Respuesta(long idUsuario, String correo, String rol, String estado, Long idPrestador) {
        static Respuesta desde(Usuario u) {
            return new Respuesta(u.id(), u.correo(), u.rol().name(), u.activo() ? "ACTIVO" : "INACTIVO", u.idPrestador());
        }
    }
}


