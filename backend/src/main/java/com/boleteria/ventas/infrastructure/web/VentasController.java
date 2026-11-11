package com.boleteria.ventas.infrastructure.web;

import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.reservas.domain.Reserva.*;
import com.boleteria.ventas.application.port.in.ConfirmarVentas;
import com.boleteria.ventas.domain.Venta.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservas/{id}")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class VentasController {
    private final ConfirmarVentas ventas;
    private final ConsultarIdentidad identidad;
    VentasController(ConfirmarVentas ventas, ConsultarIdentidad identidad) {
        this.ventas = ventas;
        this.identidad = identidad;
    }
    @GetMapping("/cotizacion") Cotizacion cotizar(Principal principal, @PathVariable long id) {
        return ventas.cotizar(actor(principal), id);
    }
    @PostMapping("/confirmar") List<Boleta> confirmar(Principal principal, @PathVariable long id,
                                                      @RequestBody Aceptacion aceptacion) {
        if (aceptacion.revision() == null || !aceptacion.revision().matches("[a-f0-9]{64}")) {
            throw new Invalida("Selecciona una cotización vigente.");
        }
        return ventas.confirmar(actor(principal), id, aceptacion.revision());
    }
    private Actor actor(Principal principal) {
        var cuenta = identidad.consultarCuenta(principal.getName()).orElseThrow(Ausente::new);
        return new Actor(cuenta.id(), cuenta.rol(), cuenta.idPrestador());
    }
    record Aceptacion(String revision) {}
    @ExceptionHandler(Recotizar.class) ProblemDetail recotizar(Recotizar error) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Revisa y acepta la cotización actualizada.");
        problema.setProperty("cotizacion", error.cotizacion());
        return problema;
    }
    @ExceptionHandler(Conflicto.class) ProblemDetail conflicto(Conflicto error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler(Invalida.class) ProblemDetail invalida(Invalida error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
    @ExceptionHandler(Ausente.class) ProblemDetail ausente() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Reserva inaccesible.");
    }
}

