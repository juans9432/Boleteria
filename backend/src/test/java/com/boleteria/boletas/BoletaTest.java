package com.boleteria.boletas;

import java.math.BigDecimal;
import java.time.*;
import com.boleteria.boletas.domain.Boleta;
import com.boleteria.boletas.domain.Boleta.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BoletaTest {
    private static final Instant APERTURA = Instant.parse("2026-11-11T01:00:00Z");
    private Boleta boleta(Estado estado, String funcion) {
        return new Boleta(1,"PRUEBA",estado,BigDecimal.ONE,1,1,"Zona",1,"Adulto",null,
                1,"Atracción",LocalDate.of(2026,11,10),LocalTime.of(20,0),LocalTime.of(21,0),funcion,null,null);
    }
    @Test void horaBogotaIncluyeAperturaYExcluyeCierre() {
        var vendida = boleta(Estado.VENDIDA,"PROGRAMADA");
        assertThatCode(() -> vendida.validarUso(APERTURA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> vendida.validarUso(APERTURA.minusNanos(1))).isInstanceOf(Conflicto.class);
        assertThatThrownBy(() -> vendida.validarUso(APERTURA.plusSeconds(3600))).isInstanceOf(Conflicto.class);
        assertThatThrownBy(() -> vendida.validarUso(APERTURA.plusSeconds(86400))).isInstanceOf(Conflicto.class);
    }
    @Test void protegeEstadosTerminalesYFuncionesNoProgramadas() {
        for (Estado estado : new Estado[]{Estado.USADA,Estado.ANULADA}) {
            assertThatThrownBy(() -> boleta(estado,"PROGRAMADA").validarUso(APERTURA)).isInstanceOf(Conflicto.class);
        }
        for (String estado : new String[]{"CANCELADA","REALIZADA"}) {
            assertThatThrownBy(() -> boleta(Estado.VENDIDA,estado).validarUso(APERTURA)).isInstanceOf(Conflicto.class);
        }
    }
}


