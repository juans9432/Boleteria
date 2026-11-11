package com.boleteria.cancelaciones;

import java.util.List;
import java.util.Set;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.cancelaciones.domain.Cancelacion;
import com.boleteria.cancelaciones.domain.Cancelacion.Entrada;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CancelacionTest {
    @Test void elEstadoFinalExigeAdultoEnLaMismaFuncion() {
        var adulto = new Entrada(1,1,1,1,null,null,Estado.VENDIDA,false,true);
        var menor = new Entrada(2,1,1,1,null,null,Estado.VENDIDA,true,false);
        var otraFuncion = new Entrada(3,1,2,1,null,null,Estado.USADA,false,true);
        var entradas = List.of(adulto,menor,otraFuncion);
        assertThatThrownBy(() -> Cancelacion.comprobarAcompanamiento(entradas, Set.of(1L)))
                .isInstanceOf(Conflicto.class);
        assertThatCode(() -> Cancelacion.comprobarAcompanamiento(entradas, Set.of(1L,2L)))
                .doesNotThrowAnyException();
    }
    @Test void unaBoletaUsadaImpideLaOperacionCompleta() {
        var usada = new Entrada(1,1,1,1,null,null,Estado.USADA,false,true);
        assertThatThrownBy(() -> Cancelacion.protegerUsadas(List.of(usada))).isInstanceOf(Conflicto.class);
    }
}

