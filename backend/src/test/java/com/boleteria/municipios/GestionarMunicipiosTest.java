package com.boleteria.municipios;

import com.boleteria.municipios.application.port.out.PersistirMunicipios;
import com.boleteria.municipios.application.usecase.GestionarMunicipiosCasoDeUso;
import com.boleteria.municipios.domain.DatosMunicipio;
import com.boleteria.municipios.domain.Municipio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GestionarMunicipiosTest {
    private final PersistirMunicipios puerto = mock(PersistirMunicipios.class);
    private final GestionarMunicipiosCasoDeUso municipios = new GestionarMunicipiosCasoDeUso(puerto);

    @Test
    void normalizaAntesDePersistir() {
        when(puerto.crear(new DatosMunicipio("Armenia", "Quindío")))
                .thenReturn(new Municipio(42, "Armenia", "Quindío"));
        assertThat(municipios.crear(" Armenia ", " Quindío ").id()).isEqualTo(42);
    }

    @Test
    void rechazaDatosInvalidosAntesDeLlamarAlPuerto() {
        assertThatThrownBy(() -> municipios.crear(" ", "Quindío")).isInstanceOf(DatosMunicipio.Invalido.class);
        assertThatThrownBy(() -> municipios.actualizar(1, "A".repeat(81), "Quindío"))
                .isInstanceOf(DatosMunicipio.Invalido.class);
        assertThatThrownBy(() -> municipios.eliminar(0)).isInstanceOf(DatosMunicipio.Invalido.class);
        verifyNoInteractions(puerto);
    }
}
