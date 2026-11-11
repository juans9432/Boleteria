package com.boleteria.perfiles.infrastructure.web;

import java.math.BigDecimal;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Acceso;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Denegado;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Rol;
import com.boleteria.perfiles.domain.Perfil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfiles")
class PerfilesController {
    private final GestionarPerfiles perfiles;
    private final ConsultarIdentidad identidad;

    PerfilesController(GestionarPerfiles perfiles, ConsultarIdentidad identidad) {
        this.perfiles = perfiles;
        this.identidad = identidad;
    }

    @GetMapping
    List<Respuesta> listar(Principal principal) {
        return perfiles.listar(acceso(principal)).stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/cuentas-vinculables")
    List<GestionarPerfiles.CuentaVinculable> cuentas(Principal principal) {
        return perfiles.cuentasVinculables(acceso(principal));
    }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(Principal principal, @PathVariable long id) {
        return ResponseEntity.of(perfiles.consultar(acceso(principal), id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var perfil = perfiles.crear(acceso(principal), solicitud.datos());
        return ResponseEntity.created(URI.create("/api/perfiles/" + perfil.id())).body(Respuesta.desde(perfil));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(perfiles.actualizar(acceso(principal), id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(Principal principal, @PathVariable long id) {
        return perfiles.eliminar(acceso(principal), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Acceso acceso(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Denegado::new);
        return switch (cuenta.rol()) {
            case ADMINISTRADOR -> new Acceso(cuenta.id(), Rol.ADMINISTRADOR);
            case OPERADOR -> new Acceso(cuenta.id(), Rol.OPERADOR);
            case CLIENTE -> new Acceso(cuenta.id(), Rol.CLIENTE);
            default -> throw new Denegado();
        };
    }

    record Solicitud(String documento, String nombre, String telefono, BigDecimal idUsuario) {
        Perfil.Datos datos() {
            try {
                return new Perfil.Datos(documento, nombre, telefono, idUsuario == null ? null : idUsuario.longValueExact());
            } catch (ArithmeticException error) {
                throw new Perfil.Invalido("Selecciona una cuenta válida.");
            }
        }
    }

    record Respuesta(long idPerfil, String documento, String nombre, String telefono, Long idUsuario, String correoCuenta) {
        static Respuesta desde(Perfil perfil) {
            var d = perfil.datos();
            return new Respuesta(perfil.id(), d.documento(), d.nombre(), d.telefono(), d.idUsuario(), perfil.correoCuenta());
        }
    }
}
