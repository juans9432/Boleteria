package com.boleteria.reservas.infrastructure.web;

import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.reservas.application.port.in.GestionarReservas;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservas")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ReservasController {
    private final GestionarReservas reservas;
    private final ConsultarIdentidad identidad;
    ReservasController(GestionarReservas reservas, ConsultarIdentidad identidad) {
        this.reservas = reservas;
        this.identidad = identidad;
    }
    @GetMapping List<Respuesta> listar(Principal principal) {
        return reservas.listar(actor(principal)).stream().map(Respuesta::de).toList();
    }
    @GetMapping("/catalogos") Object catalogos(Principal principal) { return reservas.catalogos(actor(principal)); }
    @GetMapping("/{id}") Respuesta consultar(Principal principal, @PathVariable long id) {
        return Respuesta.de(reservas.consultar(actor(principal), id));
    }
    @PostMapping ResponseEntity<Respuesta> crear(Principal principal, @RequestBody Solicitud solicitud) {
        var reserva = reservas.crear(actor(principal), solicitud);
        return ResponseEntity.created(URI.create("/api/reservas/" + reserva.id())).body(Respuesta.de(reserva));
    }
    @PutMapping("/{id}") Respuesta editar(Principal principal, @PathVariable long id, @RequestBody Solicitud solicitud) {
        return Respuesta.de(reservas.editar(actor(principal), id, solicitud));
    }
    @PostMapping("/{id}/cancelar") Respuesta cancelar(Principal principal, @PathVariable long id) {
        return Respuesta.de(reservas.cancelar(actor(principal), id));
    }

    private Actor actor(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Ausente::new);
        return new Actor(cuenta.id(), cuenta.rol(), cuenta.idPrestador());
    }
    record Respuesta(long id, long perfil, Long plan, Instant creada, Instant vence, Estado estado, List<Linea> lineas) {
        static Respuesta de(Reserva r) {
            return new Respuesta(r.id(), r.perfil(), r.plan(), r.creada(), r.vence(), r.estado(), r.lineas());
        }
    }

    @ExceptionHandler(Ausente.class) ProblemDetail ausente() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Reserva o perfil inaccesible.");
    }
    @ExceptionHandler(Conflicto.class) ProblemDetail conflicto(Conflicto error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler({Invalida.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    ProblemDetail invalida(Exception error) {
        Throwable causa = error;
        while (!(causa instanceof Invalida) && causa.getCause() != null) causa = causa.getCause();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                causa instanceof Invalida ? causa.getMessage() : "Solicitud de reserva inválida.");
    }
}


