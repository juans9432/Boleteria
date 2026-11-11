package com.boleteria.municipios;

import java.util.List;
import com.boleteria.municipios.application.usecase.ListarMunicipiosCasoDeUso;
import com.boleteria.municipios.domain.Municipio;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ListarMunicipiosTest {
    @Test
    void devuelveLosMunicipiosDelPuerto() {
        var esperado = List.of(new Municipio(1, "Armenia", "Quindio"));
        var consulta = new ListarMunicipiosCasoDeUso(() -> esperado);

        assertThat(consulta.ejecutar()).containsExactlyElementsOf(esperado);
    }

    @Test
    void devuelveListaVaciaSiElPuertoNoTieneRegistros() {
        assertThat(new ListarMunicipiosCasoDeUso(List::of).ejecutar()).isEmpty();
    }
}
