package com.boleteria.funciones.infrastructure.web;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.funciones.application.port.in.GestionarFunciones;
import com.boleteria.funciones.application.port.in.GestionarFunciones.Acceso;
import com.boleteria.funciones.application.port.in.GestionarFunciones.Denegado;
import com.boleteria.funciones.domain.DatosFuncion;
import com.boleteria.funciones.domain.Funcion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/funciones")
class FuncionesController {
    private final GestionarFunciones funciones;
    private final ConsultarIdentidad identidad;

    FuncionesController(GestionarFunciones funciones, ConsultarIdentidad identidad) {
        this.funciones = funciones;
        this.identidad = identidad;
    }

    @GetMapping
    List<Respuesta> listar(Principal principal) {
        return funciones.listar(acceso(principal)).stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(Principal principal, @PathVariable long id) {
        return ResponseEntity.of(funciones.consultar(acceso(principal), id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var creado = funciones.crear(acceso(principal), solicitud.datos());
        return ResponseEntity.created(URI.create("/api/funciones/" + creado.id())).body(Respuesta.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(funciones.actualizar(acceso(principal), id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(Principal principal, @PathVariable long id) {
        return funciones.eliminar(acceso(principal), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Acceso acceso(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Denegado::new);
        return switch (cuenta.rol()) {
            case ADMINISTRADOR -> new Acceso(true, null);
            case ENCARGADO_PRESTADOR -> new Acceso(false, cuenta.idPrestador());
            default -> throw new Denegado();
        };
    }

    record Solicitud(Long idAtraccion, String fecha, String horaApertura, String horaCierre, String estado) {
        DatosFuncion datos() { return DatosFuncion.programar(idAtraccion, fecha, horaApertura, horaCierre, estado); }
    }

    record Respuesta(long idFuncion, long idAtraccion, String fecha, String horaApertura, String horaCierre, String estado) {
        static Respuesta desde(Funcion p) {
            return new Respuesta(p.id(), p.idAtraccion(), p.fecha().toString(), p.apertura().toString(), p.cierre().toString(), p.estado().name());
        }
    }
}

