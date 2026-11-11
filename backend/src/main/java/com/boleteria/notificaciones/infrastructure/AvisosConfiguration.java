package com.boleteria.notificaciones.infrastructure;

import java.time.Clock;
import javax.sql.DataSource;
import com.boleteria.notificaciones.application.usecase.GestionarAvisos;
import com.boleteria.notificaciones.infrastructure.jdbc.AvisosOracle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AvisosConfiguration {
    @Bean GestionarAvisos gestionarAvisos(DataSource fuente) {
        return new GestionarAvisos(new AvisosOracle(fuente), Clock.systemUTC());
    }
}

