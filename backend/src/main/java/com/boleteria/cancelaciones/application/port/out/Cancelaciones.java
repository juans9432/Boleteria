package com.boleteria.cancelaciones.application.port.out;

import java.util.List;
import java.util.Optional;
import com.boleteria.cancelaciones.domain.Cancelacion.*;

public interface Cancelaciones {
    Optional<Entrada> entrada(long id);
    List<Entrada> deReserva(long id);
    List<Entrada> deFuncion(long id);
    Optional<Funcion> funcion(long id);
    void bloquearFunciones(List<Long> ids);
    void bloquearAtracciones(List<Long> ids);
    void bloquearPlan(long id);
    void bloquearBoletas(List<Long> ids);
    void anular(List<Long> ids);
    void cancelarReserva(long id);
    void cancelarFuncion(long id);
}


