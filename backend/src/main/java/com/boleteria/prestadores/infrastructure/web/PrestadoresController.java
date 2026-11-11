package com.boleteria.prestadores.infrastructure.web;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.prestadores.application.port.in.GestionarPrestadores;
import com.boleteria.prestadores.application.port.in.GestionarPrestadores.Acceso;
import com.boleteria.prestadores.application.port.in.GestionarPrestadores.Denegado;
import com.boleteria.prestadores.domain.DatosPrestador;
import com.boleteria.prestadores.domain.Prestador;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prestadores")
class PrestadoresController {
    private final GestionarPrestadores prestadores;
    private final ConsultarIdentidad identidad;

    PrestadoresController(GestionarPrestadores prestadores, ConsultarIdentidad identidad) {
        this.prestadores = prestadores;
        this.identidad = identidad;
    }

    @GetMapping
    List<Respuesta> listar(Principal principal) {
        return prestadores.listar(acceso(principal)).stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(Principal principal, @PathVariable long id) {
        return ResponseEntity.of(prestadores.consultar(acceso(principal), id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var creado = prestadores.crear(acceso(principal), solicitud.datos());
        return ResponseEntity.created(URI.create("/api/prestadores/" + creado.id())).body(Respuesta.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(prestadores.actualizar(acceso(principal), id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(Principal principal, @PathVariable long id) {
        return prestadores.eliminar(acceso(principal), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Acceso acceso(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Denegado::new);
        return switch (cuenta.rol()) {
            case ADMINISTRADOR -> new Acceso(true, null);
            case ENCARGADO_PRESTADOR -> new Acceso(false, cuenta.idPrestador());
            default -> throw new Denegado();
        };
    }

    record Solicitud(String nit, String nombre, String correo, Long idMunicipio, Long idTipoPrestador) {
        DatosPrestador datos() { return new DatosPrestador(nit, nombre, correo, idMunicipio, idTipoPrestador); }
    }

    record Respuesta(long idPrestador, String nit, String nombre, String correo, long idMunicipio, long idTipoPrestador) {
        static Respuesta desde(Prestador p) {
            return new Respuesta(p.id(), p.nit(), p.nombre(), p.correo(), p.idMunicipio(), p.idTipoPrestador());
        }
    }
}
