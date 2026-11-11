package com.boleteria.combos.infrastructure.web;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import com.boleteria.combos.application.port.in.GestionarCombos;
import com.boleteria.combos.domain.Combo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/combos")
class CombosController {
    private final GestionarCombos combos;

    CombosController(GestionarCombos combos) { this.combos = combos; }

    @GetMapping
    List<Respuesta> listar() { return combos.listar().stream().map(Respuesta::desde).toList(); }

    @GetMapping("/{id}")
    ResponseEntity<Respuesta> consultar(@PathVariable long id) {
        return ResponseEntity.of(combos.consultar(id).map(Respuesta::desde));
    }

    @PostMapping
    ResponseEntity<Respuesta> crear(@RequestBody Solicitud solicitud) {
        var creado = combos.crear(solicitud.datos());
        return ResponseEntity.created(URI.create("/api/combos/" + creado.id())).body(Respuesta.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<Respuesta> actualizar(@PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(combos.actualizar(id, solicitud.datos()).map(Respuesta::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(@PathVariable long id) {
        return combos.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    record Relacion(BigDecimal idAtraccion, BigDecimal porcentaje) {
        Combo.Atraccion dato() {
            try {
                return new Combo.Atraccion(idAtraccion == null ? 0 : idAtraccion.longValueExact(), porcentaje);
            } catch (ArithmeticException error) {
                throw new Combo.Invalido("Selecciona una atracción válida.");
            }
        }
    }

    record Solicitud(String nombre, BigDecimal precio, BigDecimal diasVigencia, String estado,
                     List<Relacion> atracciones) {
        Combo.Datos datos() {
            int dias;
            try {
                dias = diasVigencia == null ? 0 : diasVigencia.intValueExact();
            } catch (ArithmeticException error) {
                throw new Combo.Invalido("La vigencia debe ser un número entero.");
            }
            var relaciones = atracciones == null ? null
                    : atracciones.stream().map(x -> x == null ? null : x.dato()).toList();
            Combo.Estado estadoValidado;
            try {
                estadoValidado = Combo.Estado.valueOf(estado == null ? "" : estado);
            } catch (IllegalArgumentException error) {
                throw new Combo.Invalido("El estado debe ser ACTIVO o INACTIVO.");
            }
            return new Combo.Datos(nombre, precio, dias, estadoValidado, relaciones);
        }
    }

    record Respuesta(long idCombo, String nombre, BigDecimal precio, int diasVigencia,
                     Combo.Estado estado, List<Combo.Atraccion> atracciones) {
        static Respuesta desde(Combo combo) {
            var d = combo.datos();
            return new Respuesta(combo.id(), d.nombre(), d.precio(), d.diasVigencia(), d.estado(), d.atracciones());
        }
    }
}

