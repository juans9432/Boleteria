package com.boleteria.tiposentrada;

import java.math.BigDecimal;
import com.boleteria.tiposentrada.domain.TipoEntrada;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatosTipoEntradaTest {
    @Test
    void validaNombreYBanderaDeAdulto() {
        var datos = new TipoEntrada.Datos(" Infantil ", BigDecimal.ZERO, "S");
        assertThat(datos.nombre()).isEqualTo("Infantil");
        assertThat(datos.requiereAdulto()).isEqualTo("S");
        assertThatThrownBy(() -> new TipoEntrada.Datos(" ", BigDecimal.ZERO, "N"))
                .isInstanceOf(TipoEntrada.Invalido.class);
        assertThatThrownBy(() -> new TipoEntrada.Datos("a".repeat(41), BigDecimal.ZERO, "N"))
                .isInstanceOf(TipoEntrada.Invalido.class);
        assertThatThrownBy(() -> new TipoEntrada.Datos("Infantil", BigDecimal.ZERO, "X"))
                .isInstanceOf(TipoEntrada.Invalido.class);
    }

    @Test
    void admiteLimitesYHastaDosDecimales() {
        for (String valido : new String[]{"0", "12.35", "100"}) {
            assertThatCode(() -> new TipoEntrada.Datos("Entrada", new BigDecimal(valido), "N"))
                    .doesNotThrowAnyException();
        }
        for (String invalido : new String[]{"-0.01", "100.01", "1.001"}) {
            assertThatThrownBy(() -> new TipoEntrada.Datos("Entrada", new BigDecimal(invalido), "N"))
                    .isInstanceOf(TipoEntrada.Invalido.class);
        }
    }
}
