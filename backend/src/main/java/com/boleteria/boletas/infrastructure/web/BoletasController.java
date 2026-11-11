package com.boleteria.boletas.infrastructure.web;

import java.security.Principal;
import java.util.List;
import com.boleteria.boletas.application.port.in.GestionarBoletas;
import com.boleteria.boletas.domain.Boleta;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.reservas.domain.Reserva.Actor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/boletas")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class BoletasController {
    private final GestionarBoletas boletas;
    private final ConsultarIdentidad identidad;
    BoletasController(GestionarBoletas boletas, ConsultarIdentidad identidad) {
        this.boletas = boletas;
        this.identidad = identidad;
    }
    @GetMapping List<Boleta> listar(Principal principal) { return boletas.listar(actor(principal)); }
    @GetMapping("/{id}") Boleta consultar(Principal principal, @PathVariable long id) {
        return boletas.consultar(actor(principal), id);
    }
    @GetMapping("/codigo") Boleta codigo(Principal principal, @RequestParam String valor) {
        return boletas.porCodigo(actor(principal), valor);
    }
    @PostMapping("/{id}/usar") Boleta usar(Principal principal, @PathVariable long id) {
        return boletas.usar(actor(principal), id);
    }
    private Actor actor(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).filter(c -> c.habilitada()).orElseThrow(Denegado::new);
        return new Actor(cuenta.id(), cuenta.rol(), cuenta.idPrestador());
    }
    @ExceptionHandler(Ausente.class) ProblemDetail ausente() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Boleta inexistente o inaccesible.");
    }
    @ExceptionHandler(Denegado.class) ProblemDetail denegado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Tu cuenta no puede registrar el uso.");
    }
    @ExceptionHandler(Conflicto.class) ProblemDetail conflicto(Conflicto error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
}

