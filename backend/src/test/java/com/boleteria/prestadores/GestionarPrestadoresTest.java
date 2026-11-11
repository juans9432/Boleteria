package com.boleteria.prestadores;

import com.boleteria.prestadores.application.port.in.GestionarPrestadores.Acceso;
import com.boleteria.prestadores.application.port.in.GestionarPrestadores.Denegado;
import com.boleteria.prestadores.application.port.out.Prestadores;
import com.boleteria.prestadores.application.usecase.GestionarPrestadoresCasoDeUso;
import com.boleteria.prestadores.domain.DatosPrestador;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarPrestadoresTest {
    @Test
    void validaYNormalizaLosCampos() {
        var datos = new DatosPrestador(" 42 ", " Museo ", " ", 1L, 2L);
        assertThat(datos.nit()).isEqualTo("42");
        assertThat(datos.nombre()).isEqualTo("Museo");
        assertThat(datos.correo()).isNull();
        assertThatThrownBy(() -> new DatosPrestador("42", "", null, 1L, 2L))
                .isInstanceOf(DatosPrestador.Invalido.class);
        assertThatThrownBy(() -> new DatosPrestador("42", "Museo", "inválido", 1L, 2L))
                .isInstanceOf(DatosPrestador.Invalido.class);
        assertThatThrownBy(() -> new DatosPrestador("42", "Museo", null, 0L, 2L))
                .isInstanceOf(DatosPrestador.Invalido.class);
    }

    @Test
    void deniegaOperacionesFueraDelAlcanceAntesDePersistir() {
        var repositorio = mock(Prestadores.class);
        var caso = new GestionarPrestadoresCasoDeUso(repositorio);
        var encargado = new Acceso(false, 5L);
        var datos = new DatosPrestador("42", "Museo", null, 1L, 2L);
        assertThatThrownBy(() -> caso.consultar(encargado, 6)).isInstanceOf(Denegado.class);
        assertThatThrownBy(() -> caso.actualizar(encargado, 6, datos)).isInstanceOf(Denegado.class);
        assertThatThrownBy(() -> caso.crear(encargado, datos)).isInstanceOf(Denegado.class);
        assertThatThrownBy(() -> caso.eliminar(encargado, 5)).isInstanceOf(Denegado.class);
        verifyNoInteractions(repositorio);
    }
}
