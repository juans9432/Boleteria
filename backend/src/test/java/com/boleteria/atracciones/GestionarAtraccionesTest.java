package com.boleteria.atracciones;

import com.boleteria.atracciones.application.port.in.GestionarAtracciones.Acceso;
import com.boleteria.atracciones.application.port.in.GestionarAtracciones.Denegado;
import com.boleteria.atracciones.application.port.out.Atracciones;
import com.boleteria.atracciones.application.usecase.GestionarAtraccionesCasoDeUso;
import com.boleteria.atracciones.domain.DatosAtraccion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarAtraccionesTest {
    @Test
    void validaModeloYPermiteDescripcionOpcional() {
        assertThat(new DatosAtraccion("  Plan ", " ", 1L).nombre()).isEqualTo("Plan");
        assertThat(new DatosAtraccion("Plan", null, 1L).descripcion()).isNull();
        assertThatThrownBy(() -> new DatosAtraccion(" ", null, 1L))
                .isInstanceOf(DatosAtraccion.Invalido.class);
        assertThatThrownBy(() -> new DatosAtraccion("Plan", "a".repeat(501), 1L))
                .isInstanceOf(DatosAtraccion.Invalido.class);
    }

    @Test
    void impideCrearOReasignarFueraDelPrestadorAutorizado() {
        var puerto = mock(Atracciones.class);
        var caso = new GestionarAtraccionesCasoDeUso(puerto);
        var encargado = new Acceso(false, 1L);
        var ajeno = new DatosAtraccion("Plan", null, 2L);
        assertThatThrownBy(() -> caso.crear(encargado, ajeno)).isInstanceOf(Denegado.class);
        assertThatThrownBy(() -> caso.actualizar(encargado, 1, ajeno)).isInstanceOf(Denegado.class);
        verifyNoInteractions(puerto);
    }
}

