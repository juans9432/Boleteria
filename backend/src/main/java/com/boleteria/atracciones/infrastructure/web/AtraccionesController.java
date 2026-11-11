package com.boleteria.atracciones.infrastructure.web;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.atracciones.application.port.in.GestionarAtracciones;
import com.boleteria.atracciones.application.port.in.GestionarAtracciones.Acceso;
import com.boleteria.atracciones.application.port.in.GestionarAtracciones.Denegado;
import com.boleteria.atracciones.domain.DatosAtraccion;
import com.boleteria.atracciones.domain.Atraccion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/atracciones")
class AtraccionesController {
    private final GestionarAtracciones atracciones;
    private final ConsultarIdentidad identidad;

    AtraccionesController(GestionarAtracciones atracciones, ConsultarIdentidad identidad) {
        this.atracciones = atracciones;
        this.identidad = identidad;
    }

    @GetMapping
    List<Respuesta> listar(Principal principal) {
        return atracciones.listar(acceso(principal)).stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(Principal principal, @PathVariable long id) {
        return ResponseEntity.of(atracciones.consultar(acceso(principal), id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var creado = atracciones.crear(acceso(principal), solicitud.datos());
        return ResponseEntity.created(URI.create("/api/atracciones/" + creado.id())).body(Respuesta.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(atracciones.actualizar(acceso(principal), id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(Principal principal, @PathVariable long id) {
        return atracciones.eliminar(acceso(principal), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Acceso acceso(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Denegado::new);
        return switch (cuenta.rol()) {
            case ADMINISTRADOR -> new Acceso(true, null);
            case ENCARGADO_PRESTADOR -> new Acceso(false, cuenta.idPrestador());
            default -> throw new Denegado();
        };
    }

    record Solicitud(String nombre, String descripcion, Long idPrestador) {
        DatosAtraccion datos() { return new DatosAtraccion(nombre, descripcion, idPrestador); }
    }

    record Respuesta(long idAtraccion, String nombre, String descripcion, long idPrestador) {
        static Respuesta desde(Atraccion p) {
            return new Respuesta(p.id(), p.nombre(), p.descripcion(), p.idPrestador());
        }
    }
}
