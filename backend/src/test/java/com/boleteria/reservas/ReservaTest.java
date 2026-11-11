package com.boleteria.reservas;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.*;
import static org.assertj.core.api.Assertions.*;

class ReservaTest {
    @Test void elLimiteEsInclusivoYElHistorialNoSeReabre() {
        var inicio = Instant.parse("2026-11-09T15:00:00Z");
        var r = new Reserva(1, 2, null, inicio, Estado.PENDIENTE, List.of());
        assertThat(r.vigente(inicio.plusSeconds(899))).isEqualTo(Estado.PENDIENTE);
        assertThat(r.vigente(inicio.plusSeconds(900))).isEqualTo(Estado.EXPIRADA);
        assertThatThrownBy(() -> r.editable(inicio.plusSeconds(900))).isInstanceOf(Conflicto.class);
        var historica = new Reserva(2, 2, null, inicio, Estado.CONFIRMADA, List.of());
        assertThatThrownBy(() -> historica.editable(inicio)).isInstanceOf(Conflicto.class);
    }

    @Test void validaElGrupoDeCadaUnidadDelCombo() {
        assertThatThrownBy(() -> new Solicitud(1L, null, List.of(new Seleccion(1, 1, 3L, null))))
                .isInstanceOf(Invalida.class);
        assertThat(new Solicitud(1L, null,
                List.of(new Seleccion(1, 1, 3L, 1), new Seleccion(1, 1, 3L, 2))).selecciones()).hasSize(2);
    }
}

