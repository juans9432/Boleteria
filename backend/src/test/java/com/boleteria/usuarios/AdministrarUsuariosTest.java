package com.boleteria.usuarios;

import com.boleteria.identidad.domain.Rol;
import com.boleteria.usuarios.application.port.out.Usuarios;
import com.boleteria.usuarios.application.usecase.AdministrarUsuariosCasoDeUso;
import com.boleteria.usuarios.domain.Usuario;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministrarUsuariosTest {
    @Test
    void validaAsignacionDePrestador() {
        assertThatThrownBy(() -> new Usuario.Datos("encargado@test.invalid", Rol.ENCARGADO_PRESTADOR, true, null))
                .isInstanceOf(Usuario.Invalido.class);
        assertThatThrownBy(() -> new Usuario.Datos("cliente@test.invalid", Rol.CLIENTE, true, 1L))
                .isInstanceOf(Usuario.Invalido.class);
    }

    @Test
    void protegeAlUltimoAdministradorUtilizable() {
        var puerto = mock(Usuarios.class);
        when(puerto.consultar(1)).thenReturn(Optional.of(new Usuario(1, "admin@test.invalid",
                Rol.ADMINISTRADOR, true, null, true)));
        when(puerto.administradoresUtilizables()).thenReturn(1L);
        var caso = new AdministrarUsuariosCasoDeUso(puerto, clave -> { throw new AssertionError(); });
        assertThatThrownBy(() -> caso.actualizar(1, 1,
                new Usuario.Datos("admin@test.invalid", Rol.ADMINISTRADOR, false, null), null))
                .isInstanceOf(Usuario.Conflicto.class);
        verify(puerto).bloquearAdministracion();
        verify(puerto, never()).actualizar(anyLong(), any(), any());
    }
}
