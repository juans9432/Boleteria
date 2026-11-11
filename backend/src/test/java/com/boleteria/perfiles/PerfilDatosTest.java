package com.boleteria.perfiles;

import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Acceso;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Denegado;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.Rol;
import com.boleteria.perfiles.application.port.out.Perfiles;
import com.boleteria.perfiles.application.usecase.GestionarPerfilesCasoDeUso;
import com.boleteria.perfiles.domain.Perfil;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerfilDatosTest {
    @Test
    void validaDocumentoNombreTelefonoYCuentaOpcional() {
        assertThat(new Perfil.Datos(" 42 ", " Cliente ", " ", null))
                .isEqualTo(new Perfil.Datos("42", "Cliente", null, null));
        assertThatThrownBy(() -> new Perfil.Datos(" ", "Cliente", null, null)).isInstanceOf(Perfil.Invalido.class);
        assertThatThrownBy(() -> new Perfil.Datos("42", " ", null, null)).isInstanceOf(Perfil.Invalido.class);
        assertThatThrownBy(() -> new Perfil.Datos("42", "Cliente", "teléfono", null)).isInstanceOf(Perfil.Invalido.class);
        assertThatThrownBy(() -> new Perfil.Datos("42", "Cliente", null, -1L)).isInstanceOf(Perfil.Invalido.class);
    }

    @Test
    void impideAlOperadorVincularCuentas() {
        var puerto = mock(Perfiles.class);
        var caso = new GestionarPerfilesCasoDeUso(puerto);
        assertThatThrownBy(() -> caso.crear(new Acceso(1, Rol.OPERADOR), new Perfil.Datos("42", "Cliente", null, 2L)))
                .isInstanceOf(Denegado.class);
        verifyNoInteractions(puerto);
    }
}
