package com.boleteria.zonas.infrastructure.web;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.zonas.application.port.in.GestionarZonas;
import com.boleteria.zonas.application.port.in.GestionarZonas.Acceso;
import com.boleteria.zonas.application.port.in.GestionarZonas.Denegado;
import java.math.BigDecimal;
import com.boleteria.zonas.domain.Zona;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zonas")
class ZonasController {
    private final GestionarZonas zonas;
    private final ConsultarIdentidad identidad;

    ZonasController(GestionarZonas zonas, ConsultarIdentidad identidad) {
        this.zonas = zonas;
        this.identidad = identidad;
    }

    @GetMapping
    List<Respuesta> listar(Principal principal, @RequestParam(required = false) Long idFuncion) {
        return zonas.listar(acceso(principal), idFuncion).stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(Principal principal, @PathVariable long id) {
        return ResponseEntity.of(zonas.consultar(acceso(principal), id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Alta solicitud) {
        var creado = zonas.crear(acceso(principal), solicitud.idFuncion() == null ? 0 : solicitud.idFuncion(), solicitud.datos());
        return ResponseEntity.created(URI.create("/api/zonas/" + creado.id())).body(Respuesta.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(zonas.actualizar(acceso(principal), id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(Principal principal, @PathVariable long id) {
        return zonas.eliminar(acceso(principal), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Acceso acceso(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Denegado::new);
        return switch (cuenta.rol()) {
            case ADMINISTRADOR -> new Acceso(true, null);
            case ENCARGADO_PRESTADOR -> new Acceso(false, cuenta.idPrestador());
            default -> throw new Denegado();
        };
    }

    record Alta(Long idFuncion, String nombre, BigDecimal aforo, BigDecimal precio) {
        Zona.Datos datos() { return Zona.Datos.desde(nombre, aforo, precio); }
    }

    record Solicitud(String nombre, BigDecimal aforo, BigDecimal precio) {
        Zona.Datos datos() { return Zona.Datos.desde(nombre, aforo, precio); }
    }

    record Respuesta(long idZona, long idFuncion, String nombre, int aforo, BigDecimal precio) {
        static Respuesta desde(Zona z) {
            return new Respuesta(z.id(), z.idFuncion(), z.nombre(), z.aforo(), z.precio());
        }
    }
}
