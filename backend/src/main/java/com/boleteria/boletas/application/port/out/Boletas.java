package com.boleteria.boletas.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.boletas.domain.Boleta;

public interface Boletas {
    record Alcance(Long usuario, Long prestador) {}
    List<Boleta> listar(Alcance alcance);
    Optional<Boleta> consultar(Alcance alcance, long id);
    Optional<Boleta> porCodigo(Alcance alcance, String codigo);
    void bloquearUso(long funcion, long boleta);
    boolean marcarUsada(long id);
}

