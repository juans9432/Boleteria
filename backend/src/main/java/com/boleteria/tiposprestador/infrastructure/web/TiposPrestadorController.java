package com.boleteria.tiposprestador.infrastructure.web;

import java.net.URI;
import java.util.List;
import com.boleteria.tiposprestador.application.port.in.GestionarTiposPrestador;
import com.boleteria.tiposprestador.domain.TipoPrestador;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tipos-prestador")
class TiposPrestadorController {
    private final GestionarTiposPrestador tipos;

    TiposPrestadorController(GestionarTiposPrestador tipos) {
        this.tipos = tipos;
    }

    @GetMapping
    List<TipoResponse> listar() {
        return tipos.listar().stream().map(TipoResponse::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<TipoResponse> consultar(@PathVariable long id) {
        return ResponseEntity.of(tipos.consultar(id).map(TipoResponse::desde));
    }

    @PostMapping
    ResponseEntity<TipoResponse> crear(@RequestBody Solicitud solicitud) {
        var creado = tipos.crear(solicitud.nombre());
        return ResponseEntity.created(URI.create("/api/tipos-prestador/" + creado.id()))
                .body(TipoResponse.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<TipoResponse> actualizar(@PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(tipos.actualizar(id, solicitud.nombre()).map(TipoResponse::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(@PathVariable long id) {
        return tipos.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    record Solicitud(String nombre) {}
    record TipoResponse(long idTipoPrestador, String nombre) {
        static TipoResponse desde(TipoPrestador tipo) {
            return new TipoResponse(tipo.id(), tipo.nombre());
        }
    }
}
