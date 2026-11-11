package com.boleteria.municipios.infrastructure.web;

import java.net.URI;
import java.util.List;
import com.boleteria.municipios.application.port.in.GestionarMunicipios;
import com.boleteria.municipios.application.port.in.ListarMunicipios;
import com.boleteria.municipios.domain.Municipio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/municipios")
class MunicipiosController {
    private final ListarMunicipios listarMunicipios;
    private final GestionarMunicipios municipios;

    MunicipiosController(ListarMunicipios listarMunicipios, GestionarMunicipios municipios) {
        this.listarMunicipios = listarMunicipios;
        this.municipios = municipios;
    }

    @GetMapping
    List<MunicipioResponse> listar() {
        return listarMunicipios.ejecutar().stream().map(MunicipioResponse::desde).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<MunicipioResponse> consultar(@PathVariable long id) {
        return ResponseEntity.of(municipios.consultar(id).map(MunicipioResponse::desde));
    }

    @PostMapping
    ResponseEntity<MunicipioResponse> crear(@RequestBody Solicitud solicitud) {
        Municipio creado = municipios.crear(solicitud.nombre(), solicitud.departamento());
        return ResponseEntity.created(URI.create("/api/municipios/" + creado.id()))
                .body(MunicipioResponse.desde(creado));
    }

    @PutMapping("/{id}")
    ResponseEntity<MunicipioResponse> actualizar(@PathVariable long id, @RequestBody Solicitud solicitud) {
        return ResponseEntity.of(municipios.actualizar(id, solicitud.nombre(), solicitud.departamento())
                .map(MunicipioResponse::desde));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> eliminar(@PathVariable long id) {
        return municipios.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    record Solicitud(String nombre, String departamento) {
    }

    record MunicipioResponse(long idMunicipio, String nombre, String departamento) {
        static MunicipioResponse desde(Municipio municipio) {
            return new MunicipioResponse(municipio.id(), municipio.nombre(), municipio.departamento());
        }
    }
}
