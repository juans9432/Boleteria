package com.boleteria.municipios.infrastructure;

import javax.sql.DataSource;
import com.boleteria.municipios.application.port.in.GestionarMunicipios;
import com.boleteria.municipios.application.port.in.ListarMunicipios;
import com.boleteria.municipios.application.port.out.ConsultarMunicipios;
import com.boleteria.municipios.application.port.out.PersistirMunicipios;
import com.boleteria.municipios.application.usecase.GestionarMunicipiosCasoDeUso;
import com.boleteria.municipios.application.usecase.ListarMunicipiosCasoDeUso;
import com.boleteria.municipios.infrastructure.jdbc.MunicipiosOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MunicipiosConfiguration {
    @Bean
    MunicipiosOracle municipiosOracle(DataSource dataSource) {
        return new MunicipiosOracle(dataSource);
    }

    @Bean
    ListarMunicipios listarMunicipios(ConsultarMunicipios municipios) {
        return new ListarMunicipiosCasoDeUso(municipios);
    }

    @Bean
    GestionarMunicipios gestionarMunicipios(PersistirMunicipios municipios) {
        return new GestionarMunicipiosCasoDeUso(municipios);
    }
}
