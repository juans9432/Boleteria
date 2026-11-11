package com.boleteria.ventas.application.port.out;

import java.util.List;

import java.util.Optional;
import com.boleteria.ventas.domain.Venta.*;

public interface Emision {
    enum Recurso { FUNCION, ATRACCION, ZONA, PLAN, COMBO, TIPO }
    void bloquear(Recurso recurso, List<Long> ids);
    Optional<Tipo> tipo(long id);
    List<Integer> numeros(long reserva);
    int disponibles(long zona);
    int activas(long perfil, long funcion);
    List<Boleta> emitidas(long reserva);
    List<Boleta> emitir(long reserva, List<Linea> lineas);
}


