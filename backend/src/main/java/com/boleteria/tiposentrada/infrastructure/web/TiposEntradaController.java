package com.boleteria.tiposentrada.infrastructure.web;

import java.net.URI;
import java.math.BigDecimal;
import java.util.List;
import com.boleteria.tiposentrada.application.port.in.GestionarTiposEntrada;
import com.boleteria.tiposentrada.domain.TipoEntrada;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tipos-entrada")
class TiposEntradaController {
    private final GestionarTiposEntrada tipos;

    TiposEntradaController(GestionarTiposEntrada tipos) {
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
        var creado = tipos.crear(solicitud.datos());
        return ResponseEntity.created(URI.create("/api/tipos-entrada/" + creado.id()))
                .body(TipoResponse.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<TipoResponse> actualizar(@PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(tipos.actualizar(id, solicitud.datos()).map(TipoResponse::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(@PathVariable long id) {
        return tipos.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    record Solicitud(String nombre, BigDecimal porcentajeDescuento, String requiereAdulto) {
        TipoEntrada.Datos datos() { return new TipoEntrada.Datos(nombre, porcentajeDescuento, requiereAdulto); }
    }
    record TipoResponse(long idTipoEntrada, String nombre, BigDecimal porcentajeDescuento, String requiereAdulto) {
        static TipoResponse desde(TipoEntrada tipo) {
            return new TipoResponse(tipo.id(), tipo.datos().nombre(), tipo.datos().porcentajeDescuento(), tipo.datos().requiereAdulto());
        }
    }
}

