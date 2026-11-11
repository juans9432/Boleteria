package com.boleteria.zonas;

import java.math.BigDecimal;
import com.boleteria.zonas.domain.Zona;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatosZonaTest {
    @Test
    void validaPrecisionAforoYNombre() {
        assertThat(Zona.Datos.desde(" General ", new BigDecimal("1.0"), new BigDecimal("10.50")).nombre())
                .isEqualTo("General");
        assertThatThrownBy(() -> Zona.Datos.desde("General", new BigDecimal("1.5"), BigDecimal.ONE))
                .isInstanceOf(Zona.Invalida.class);
        assertThatThrownBy(() -> new Zona.Datos(" ", 1, BigDecimal.ONE)).isInstanceOf(Zona.Invalida.class);
        assertThatThrownBy(() -> new Zona.Datos("General", 0, BigDecimal.ONE)).isInstanceOf(Zona.Invalida.class);
        assertThatThrownBy(() -> new Zona.Datos("General", 1, new BigDecimal("1.001"))).isInstanceOf(Zona.Invalida.class);
        assertThatThrownBy(() -> new Zona.Datos("General", 1, BigDecimal.ZERO)).isInstanceOf(Zona.Invalida.class);
    }

    @Test
    void permiteAforoIgualALaOcupacion() {
        var datos = new Zona.Datos("General", 2, BigDecimal.ONE);
        assertThatCode(() -> datos.verificarOcupacion(2)).doesNotThrowAnyException();
        assertThatThrownBy(() -> datos.verificarOcupacion(3)).isInstanceOf(Zona.Conflicto.class);
    }
}
