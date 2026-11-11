package com.boleteria.cancelaciones.infrastructure.web;

import java.security.Principal;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.cancelaciones.application.port.in.GestionarCancelaciones;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.reservas.domain.Reserva.Actor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CancelacionesController {
    private final GestionarCancelaciones casos;
    private final ConsultarIdentidad identidad;
    CancelacionesController(GestionarCancelaciones casos, ConsultarIdentidad identidad) {
        this.casos = casos;
        this.identidad = identidad;
    }
    @PostMapping("/boletas/{id}/anular") @ResponseStatus(HttpStatus.NO_CONTENT)
    void anular(Principal principal, @PathVariable long id) { casos.anularBoleta(actor(principal), id); }
    @PostMapping("/boletas/{id}/anular-combo") @ResponseStatus(HttpStatus.NO_CONTENT)
    void combo(Principal principal, @PathVariable long id) { casos.anularCombo(actor(principal), id); }
    @PostMapping("/reservas/{id}/cancelar-confirmada") @ResponseStatus(HttpStatus.NO_CONTENT)
    void reserva(Principal principal, @PathVariable long id) { casos.cancelarReserva(actor(principal), id); }
    @PostMapping("/funciones/{id}/cancelar") @ResponseStatus(HttpStatus.NO_CONTENT)
    void funcion(Principal principal, @PathVariable long id) { casos.cancelarFuncion(actor(principal), id); }
    private Actor actor(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).filter(c -> c.habilitada()).orElseThrow(Denegado::new);
        return new Actor(cuenta.id(), cuenta.rol(), cuenta.idPrestador());
    }
    @ExceptionHandler(Ausente.class) ProblemDetail ausente() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Registro inexistente o fuera de tu alcance.");
    }
    @ExceptionHandler(Denegado.class) ProblemDetail denegado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Tu cuenta no puede realizar esta operación.");
    }
    @ExceptionHandler({Conflicto.class, com.boleteria.reservas.domain.Reserva.Conflicto.class})
    ProblemDetail conflicto(RuntimeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
}

