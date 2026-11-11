package com.boleteria.identidad.infrastructure;

import javax.sql.DataSource;
import com.boleteria.identidad.application.port.in.ConfigurarClaveInicial;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.identidad.application.port.out.Cuentas;
import com.boleteria.identidad.application.usecase.Identidad;
import com.boleteria.identidad.application.usecase.ProvisionarClave;
import com.boleteria.identidad.infrastructure.jdbc.CuentasOracle;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class IdentidadConfiguration {
    @Bean
    Cuentas cuentas(DataSource dataSource) {
        return new CuentasOracle(dataSource);
    }

    @Bean
    ConsultarIdentidad consultarIdentidad(Cuentas cuentas) {
        return new Identidad(cuentas);
    }

    @Bean
    PasswordEncoder codificador() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Profile("provisionar-clave")
    ApplicationRunner provisionarClave(Cuentas cuentas, PasswordEncoder encoder, Environment entorno) {
        ConfigurarClaveInicial provision = new ProvisionarClave(cuentas, encoder::encode);
        return args -> {
            if (!provision.ejecutar(entorno.getRequiredProperty("BOLETERIA_CUENTA_CORREO"),
                    entorno.getRequiredProperty("BOLETERIA_CUENTA_CLAVE"))) {
                throw new IllegalStateException("No se encontró una cuenta con clave pendiente.");
            }
            System.out.println("Clave inicial configurada.");
        };
    }
}
