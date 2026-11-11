package com.boleteria.identidad.infrastructure.web;

import java.security.Principal;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class IdentidadController {
    private final ConsultarIdentidad identidad;

    IdentidadController(ConsultarIdentidad identidad) {
        this.identidad = identidad;
    }

    @GetMapping("/api/auth/csrf")
    Token csrf(CsrfToken token) {
        return new Token(token.getToken());
    }

    @GetMapping("/api/auth/sesion")
    Sesion sesion(Principal principal) {
        return identidad.consultarCuenta(principal.getName())
                .map(cuenta -> new Sesion(cuenta.correo(), cuenta.rol().name(), cuenta.idPrestador()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/api/auth/prestadores/{idPrestador}/acceso")
    ResponseEntity<Void> acceso(Principal principal, @PathVariable long idPrestador) {
        return identidad.puedeAdministrar(principal.getName(), idPrestador)
                ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    record Token(String token) {}
    record Sesion(String correo, String rol, Long idPrestador) {}
}
