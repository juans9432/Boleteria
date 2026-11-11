package com.boleteria.combos;

import java.math.BigDecimal;
import java.util.List;
import com.boleteria.combos.domain.Combo;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ComboDatosTest {
    @Test
    void validaImportesVigenciaYDuplicados() {
        var a = new Combo.Atraccion(1, new BigDecimal("50"));
        assertThatThrownBy(() -> datos(Combo.Estado.INACTIVO, List.of(a, a))).isInstanceOf(Combo.Invalido.class);
        assertThatThrownBy(() -> new Combo.Atraccion(1, new BigDecimal("1.001"))).isInstanceOf(Combo.Invalido.class);
        assertThatThrownBy(() -> new Combo.Datos(" ", BigDecimal.ONE, 1, Combo.Estado.INACTIVO, List.of()))
                .isInstanceOf(Combo.Invalido.class);
        assertThatThrownBy(() -> new Combo.Datos("Combo", BigDecimal.ZERO, 1, Combo.Estado.INACTIVO, List.of()))
                .isInstanceOf(Combo.Invalido.class);
        assertThatThrownBy(() -> new Combo.Datos("Combo", BigDecimal.ONE, 1000, Combo.Estado.INACTIVO, List.of()))
                .isInstanceOf(Combo.Invalido.class);
    }

    @Test
    void permiteBorradoresYExigeCienExactoParaActivar() {
        assertThatCode(() -> datos(Combo.Estado.INACTIVO, List.of()).verificarActivacion()).doesNotThrowAnyException();
        var primera = new Combo.Atraccion(1, new BigDecimal("33.33"));
        var segunda = new Combo.Atraccion(2, new BigDecimal("66.67"));
        assertThatCode(() -> datos(Combo.Estado.ACTIVO, List.of(primera, segunda)).verificarActivacion())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> datos(Combo.Estado.ACTIVO, List.of(primera)).verificarActivacion())
                .isInstanceOf(Combo.Invalido.class);
    }

    private Combo.Datos datos(Combo.Estado estado, List<Combo.Atraccion> atracciones) {
        return new Combo.Datos("Combo", new BigDecimal("1000"), 1, estado, atracciones);
    }
}
