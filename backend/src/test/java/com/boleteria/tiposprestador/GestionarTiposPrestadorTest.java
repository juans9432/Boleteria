package com.boleteria.tiposprestador;

import com.boleteria.tiposprestador.application.port.out.TiposPrestador;
import com.boleteria.tiposprestador.application.usecase.GestionarTiposPrestadorCasoDeUso;
import com.boleteria.tiposprestador.domain.TipoPrestador;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarTiposPrestadorTest {
    private final TiposPrestador puerto = mock(TiposPrestador.class);
    private final GestionarTiposPrestadorCasoDeUso tipos = new GestionarTiposPrestadorCasoDeUso(puerto);

    @Test
    void normalizaElNombreAntesDeCrear() {
        when(puerto.crear("Museo")).thenReturn(new TipoPrestador(1, "Museo"));
        assertThat(tipos.crear(" Museo ")).isEqualTo(new TipoPrestador(1, "Museo"));
    }

    @Test
    void rechazaDatosInvalidosAntesDePersistir() {
        assertThatThrownBy(() -> tipos.crear(" ")).isInstanceOf(TipoPrestador.Invalido.class);
        assertThatThrownBy(() -> tipos.actualizar(1, "A".repeat(61))).isInstanceOf(TipoPrestador.Invalido.class);
        assertThatThrownBy(() -> tipos.eliminar(0)).isInstanceOf(TipoPrestador.Invalido.class);
        verifyNoInteractions(puerto);
    }
}
