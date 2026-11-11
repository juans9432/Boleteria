package com.boleteria.cancelaciones.domain;

import java.util.List;
import java.util.Set;
import com.boleteria.boletas.domain.Boleta.Estado;
import com.boleteria.boletas.domain.Boleta.Conflicto;

public final class Cancelacion {
    private Cancelacion() {}
    public record Entrada(long id, long reserva, long funcion, long prestador, Long combo, Integer grupo,
                          Estado estado, boolean requiereAdulto, boolean adulto, String codigo) {
        public Entrada(long id, long reserva, long funcion, long prestador, Long combo, Integer grupo,
                       Estado estado, boolean requiereAdulto, boolean adulto) {
            this(id, reserva, funcion, prestador, combo, grupo, estado, requiereAdulto, adulto, null);
        }
    }
    public record Funcion(long atraccion, long prestador, String estado) {}
    public static void protegerUsadas(List<Entrada> entradas) {
        if (entradas.stream().anyMatch(e -> e.estado() == Estado.USADA)) {
            throw new Conflicto("La operación afecta una boleta USADA.");
        }
    }
    public static void comprobarAcompanamiento(List<Entrada> entradas, Set<Long> anuladas) {
        var vigentes = entradas.stream().filter(e -> e.estado() != Estado.ANULADA && !anuladas.contains(e.id())).toList();
        for (var menor : vigentes) {
            if (menor.requiereAdulto() && vigentes.stream().noneMatch(a -> a.adulto()
                    && a.reserva() == menor.reserva() && a.funcion() == menor.funcion())) {
                throw new Conflicto("La anulación dejaría una entrada sin acompañamiento adulto en su función.");
            }
        }
    }
}


