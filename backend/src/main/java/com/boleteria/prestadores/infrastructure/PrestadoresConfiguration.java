package com.boleteria.prestadores.infrastructure;

import javax.sql.DataSource;
import com.boleteria.prestadores.application.port.in.GestionarPrestadores;
import com.boleteria.prestadores.application.port.out.Prestadores;
import com.boleteria.prestadores.application.usecase.GestionarPrestadoresCasoDeUso;
import com.boleteria.prestadores.infrastructure.jdbc.PrestadoresOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class PrestadoresConfiguration {
    @Bean
    Prestadores prestadores(DataSource dataSource) {
        return new PrestadoresOracle(dataSource);
    }

    @Bean
    GestionarPrestadores gestionarPrestadores(Prestadores prestadores, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarPrestadoresCasoDeUso(prestadores));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarPrestadores.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarPrestadores) proxy.getObject();
    }
}

