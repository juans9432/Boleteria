package com.boleteria.planes;

import com.boleteria.planes.application.port.in.GestionarPlanes.Acceso;
import com.boleteria.planes.application.port.in.GestionarPlanes.Denegado;
import com.boleteria.planes.application.port.out.Planes;
import com.boleteria.planes.application.usecase.GestionarPlanesCasoDeUso;
import com.boleteria.planes.domain.DatosPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarPlanesTest {
    @Test
    void validaModeloYPermiteDescripcionOpcional() {
        assertThat(new DatosPlan("  Plan ", " ", "ACTIVO", 1L).nombre()).isEqualTo("Plan");
        assertThat(new DatosPlan("Plan", null, "INACTIVO", 1L).descripcion()).isNull();
        assertThatThrownBy(() -> new DatosPlan("Plan", null, "CANCELADO", 1L))
                .isInstanceOf(DatosPlan.Invalido.class);
        assertThatThrownBy(() -> new DatosPlan("Plan", "a".repeat(501), "ACTIVO", 1L))
                .isInstanceOf(DatosPlan.Invalido.class);
    }

    @Test
    void impideCrearOReasignarFueraDelPrestadorAutorizado() {
        var puerto = mock(Planes.class);
        var caso = new GestionarPlanesCasoDeUso(puerto);
        var encargado = new Acceso(false, 1L);
        var ajeno = new DatosPlan("Plan", null, "ACTIVO", 2L);
        assertThatThrownBy(() -> caso.crear(encargado, ajeno)).isInstanceOf(Denegado.class);
        assertThatThrownBy(() -> caso.actualizar(encargado, 1, ajeno)).isInstanceOf(Denegado.class);
        verifyNoInteractions(puerto);
    }
}
