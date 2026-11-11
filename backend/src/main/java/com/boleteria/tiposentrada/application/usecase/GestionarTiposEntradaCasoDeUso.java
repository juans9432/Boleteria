package com.boleteria.tiposentrada.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.tiposentrada.application.port.in.GestionarTiposEntrada;
import com.boleteria.tiposentrada.application.port.out.TiposEntrada;
import com.boleteria.tiposentrada.domain.TipoEntrada;

public class GestionarTiposEntradaCasoDeUso implements GestionarTiposEntrada {
    private final TiposEntrada tipos;

    public GestionarTiposEntradaCasoDeUso(TiposEntrada tipos) {
        this.tipos = tipos;
    }

    @Override
    public List<TipoEntrada> listar() {
        return tipos.listar();
    }

    @Override
    public Optional<TipoEntrada> consultar(long id) {
        validarId(id);
        return tipos.consultar(id);
    }

    @Override
    public TipoEntrada crear(TipoEntrada.Datos datos) {
        return tipos.crear(datos);
    }

    @Override
    public Optional<TipoEntrada> actualizar(long id, TipoEntrada.Datos datos) {
        validarId(id);
        return tipos.bloquear(id).flatMap(actual -> {
            var anterior = actual.datos();
            boolean cambiaAdulto = "ADULTO".equalsIgnoreCase(anterior.nombre()) != "ADULTO".equalsIgnoreCase(datos.nombre());
            if ((cambiaAdulto || !anterior.requiereAdulto().equals(datos.requiereAdulto())) && tipos.tieneBoletas(id)) {
                throw new TipoEntrada.SemanticaHistorica();
            }
            var tipo = new TipoEntrada(id, datos);
            return tipos.actualizar(tipo) ? Optional.of(tipo) : Optional.empty();
        });
    }

    @Override
    public boolean eliminar(long id) {
        validarId(id);
        return tipos.eliminar(id);
    }

    private static void validarId(long id) {
        if (id < 1) throw new TipoEntrada.Invalido("El ID debe ser positivo.");
    }
}


