package com.boleteria.funciones;

import java.time.LocalDate;
import java.time.LocalTime;
import com.boleteria.funciones.domain.DatosFuncion;
import com.boleteria.funciones.domain.Funcion;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HorarioFuncionTest {
    @Test
    void validaFechaHoraOrdenYEstado() {
        assertThatThrownBy(() -> DatosFuncion.programar(1L, "2027-02-29", "09:00", "10:00", "PROGRAMADA"))
                .isInstanceOf(DatosFuncion.Invalida.class);
        assertThatThrownBy(() -> DatosFuncion.programar(1L, "2028-02-29", "9:00", "10:00", "PROGRAMADA"))
                .isInstanceOf(DatosFuncion.Invalida.class);
        assertThatThrownBy(() -> DatosFuncion.programar(1L, "2028-02-29", "24:00", "10:00", "PROGRAMADA"))
                .isInstanceOf(DatosFuncion.Invalida.class);
        assertThatThrownBy(() -> DatosFuncion.programar(1L, "2028-02-29", "10:00", "10:00", "PROGRAMADA"))
                .isInstanceOf(DatosFuncion.Invalida.class);
        assertThatThrownBy(() -> DatosFuncion.programar(1L, "2028-02-29", "09:00", "10:00", "CANCELADA"))
                .isInstanceOf(DatosFuncion.Invalida.class);
        assertThat(DatosFuncion.programar(1L, "2028-02-29", "09:00", "10:00", "PROGRAMADA").fecha())
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void permiteHorasContiguasYExcluyeCanceladas() {
        var datos = DatosFuncion.programar(1L, "2028-02-29", "09:00", "10:00", "PROGRAMADA");
        assertThat(new Funcion(1, 1, datos.fecha(), LocalTime.of(10, 0), LocalTime.of(11, 0),
                Funcion.Estado.PROGRAMADA).cruza(datos)).isFalse();
        assertThat(new Funcion(2, 1, datos.fecha(), LocalTime.of(9, 30), LocalTime.of(10, 30),
                Funcion.Estado.REALIZADA).cruza(datos)).isTrue();
        assertThat(new Funcion(3, 1, datos.fecha(), LocalTime.of(9, 30), LocalTime.of(10, 30),
                Funcion.Estado.CANCELADA).cruza(datos)).isFalse();
    }
}
