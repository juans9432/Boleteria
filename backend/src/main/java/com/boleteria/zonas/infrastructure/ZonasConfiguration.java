package com.boleteria.zonas.infrastructure;

import javax.sql.DataSource;
import com.boleteria.zonas.application.port.in.GestionarZonas;
import com.boleteria.zonas.application.port.out.Zonas;
import com.boleteria.zonas.application.usecase.GestionarZonasCasoDeUso;
import com.boleteria.zonas.infrastructure.jdbc.ZonasOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class ZonasConfiguration {
    @Bean
    Zonas zonas(DataSource dataSource) {
        return new ZonasOracle(dataSource);
    }

    @Bean
    GestionarZonas gestionarZonas(Zonas zonas, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("actualizar", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("eliminar", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarZonasCasoDeUso(zonas));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarZonas.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarZonas) proxy.getObject();
    }
}




