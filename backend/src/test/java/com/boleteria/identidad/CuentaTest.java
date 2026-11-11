package com.boleteria.identidad;

import com.boleteria.identidad.domain.Cuenta;
import com.boleteria.identidad.domain.Rol;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CuentaTest {
    @Test
    void encargadoSoloPuedeAdministrarSuPrestador() {
        var cuenta = new Cuenta(1, "encargado@example.invalid", Rol.ENCARGADO_PRESTADOR, true, 10L);
        assertThat(cuenta.puedeAdministrar(10)).isTrue();
        assertThat(cuenta.puedeAdministrar(11)).isFalse();
        assertThat(new Cuenta(1, "x", Rol.ENCARGADO_PRESTADOR, true, null).habilitada()).isFalse();
    }

    @Test
    void soloAdministradorTieneAlcanceGeneral() {
        assertThat(new Cuenta(1, "x", Rol.ADMINISTRADOR, true, null).puedeAdministrar(20)).isTrue();
        assertThat(new Cuenta(1, "x", Rol.OPERADOR, true, null).puedeAdministrar(20)).isFalse();
        assertThat(new Cuenta(1, "x", Rol.CLIENTE, true, null).puedeAdministrar(20)).isFalse();
        assertThat(new Cuenta(1, "x", Rol.ADMINISTRADOR, false, null).puedeAdministrar(20)).isFalse();
    }
}
