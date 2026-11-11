package com.boleteria.notificaciones.infrastructure.web;

import java.security.Principal;
import java.util.List;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.notificaciones.application.port.in.ConsultarAvisos;
import com.boleteria.notificaciones.domain.Aviso;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificaciones")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AvisosController {
    private final ConsultarAvisos avisos;
    private final ConsultarIdentidad identidad;
    AvisosController(ConsultarAvisos avisos, ConsultarIdentidad identidad) {
        this.avisos = avisos;
        this.identidad = identidad;
    }
    @GetMapping List<Aviso> listar(Principal principal) { return avisos.listar(usuario(principal)); }
    @GetMapping("/{id}") Aviso consultar(Principal principal, @PathVariable long id) {
        return avisos.consultar(usuario(principal), id);
    }
    @PostMapping("/{id}/leer") Aviso leer(Principal principal, @PathVariable long id) {
        return avisos.leer(usuario(principal), id);
    }
    private long usuario(Principal principal) {
        return identidad.consultarCuenta(principal.getName()).filter(c -> c.habilitada())
                .orElseThrow(Aviso.Ausente::new).id();
    }
    @ExceptionHandler(Aviso.Ausente.class) ProblemDetail ausente() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Notificación inexistente o fuera de tu bandeja.");
    }
}

