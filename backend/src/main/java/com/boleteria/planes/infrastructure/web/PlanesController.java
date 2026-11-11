package com.boleteria.planes.infrastructure.web;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.planes.application.port.in.GestionarPlanes;
import com.boleteria.planes.application.port.in.GestionarPlanes.Acceso;
import com.boleteria.planes.application.port.in.GestionarPlanes.Denegado;
import com.boleteria.planes.domain.DatosPlan;
import com.boleteria.planes.domain.Plan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planes")
class PlanesController {
    private final GestionarPlanes planes;
    private final ConsultarIdentidad identidad;

    PlanesController(GestionarPlanes planes, ConsultarIdentidad identidad) {
        this.planes = planes;
        this.identidad = identidad;
    }

    @GetMapping
    List<Respuesta> listar(Principal principal) {
        return planes.listar(acceso(principal)).stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(Principal principal, @PathVariable long id) {
        return ResponseEntity.of(planes.consultar(acceso(principal), id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var creado = planes.crear(acceso(principal), solicitud.datos());
        return ResponseEntity.created(URI.create("/api/planes/" + creado.id())).body(Respuesta.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(planes.actualizar(acceso(principal), id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(Principal principal, @PathVariable long id) {
        return planes.eliminar(acceso(principal), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Acceso acceso(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Denegado::new);
        return switch (cuenta.rol()) {
            case ADMINISTRADOR -> new Acceso(true, null);
            case ENCARGADO_PRESTADOR -> new Acceso(false, cuenta.idPrestador());
            default -> throw new Denegado();
        };
    }

    record Solicitud(String nombre, String descripcion, String estado, Long idPrestador) {
        DatosPlan datos() { return new DatosPlan(nombre, descripcion, estado, idPrestador); }
    }

    record Respuesta(long idPlan, String nombre, String descripcion, String estado, long idPrestador) {
        static Respuesta desde(Plan p) {
            return new Respuesta(p.id(), p.nombre(), p.descripcion(), p.estado(), p.idPrestador());
        }
    }
}
