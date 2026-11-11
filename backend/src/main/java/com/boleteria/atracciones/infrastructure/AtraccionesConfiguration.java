package com.boleteria.atracciones.infrastructure;

import javax.sql.DataSource;
import com.boleteria.atracciones.application.port.in.GestionarAtracciones;
import com.boleteria.atracciones.application.port.out.Atracciones;
import com.boleteria.atracciones.application.usecase.GestionarAtraccionesCasoDeUso;
import com.boleteria.atracciones.infrastructure.jdbc.AtraccionesOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class AtraccionesConfiguration {
    @Bean
    Atracciones atracciones(DataSource dataSource) {
        return new AtraccionesOracle(dataSource);
    }

    @Bean
    GestionarAtracciones gestionarAtracciones(Atracciones atracciones, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarAtraccionesCasoDeUso(atracciones));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarAtracciones.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarAtracciones) proxy.getObject();
    }
}
