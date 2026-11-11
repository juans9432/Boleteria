package com.boleteria.tiposprestador.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.tiposprestador.application.port.in.GestionarTiposPrestador;
import com.boleteria.tiposprestador.application.port.out.TiposPrestador;
import com.boleteria.tiposprestador.domain.TipoPrestador;

public class GestionarTiposPrestadorCasoDeUso implements GestionarTiposPrestador {
    private final TiposPrestador tipos;

    public GestionarTiposPrestadorCasoDeUso(TiposPrestador tipos) {
        this.tipos = tipos;
    }

    @Override
    public List<TipoPrestador> listar() {
        return tipos.listar();
    }

    @Override
    public Optional<TipoPrestador> consultar(long id) {
        validarId(id);
        return tipos.consultar(id);
    }

    @Override
    public TipoPrestador crear(String nombre) {
        return tipos.crear(TipoPrestador.validarNombre(nombre));
    }

    @Override
    public Optional<TipoPrestador> actualizar(long id, String nombre) {
        var tipo = new TipoPrestador(id, nombre);
        return tipos.actualizar(tipo) ? Optional.of(tipo) : Optional.empty();
    }

    @Override
    public boolean eliminar(long id) {
        validarId(id);
        return tipos.eliminar(id);
    }

    private static void validarId(long id) {
        if (id < 1) throw new TipoPrestador.Invalido("El ID debe ser positivo.");
    }
}
