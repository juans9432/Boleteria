package com.boleteria.tiposentrada.infrastructure;

import javax.sql.DataSource;
import com.boleteria.tiposentrada.application.port.in.GestionarTiposEntrada;
import com.boleteria.tiposentrada.application.port.out.TiposEntrada;
import com.boleteria.tiposentrada.application.usecase.GestionarTiposEntradaCasoDeUso;
import com.boleteria.tiposentrada.infrastructure.jdbc.TiposEntradaOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class TiposEntradaConfiguration {
    @Bean
    TiposEntrada tiposEntrada(DataSource dataSource) {
        return new TiposEntradaOracle(dataSource);
    }

    @Bean
    GestionarTiposEntrada gestionarTiposEntrada(TiposEntrada tipos, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());

        transacciones.addTransactionalMethod("actualizar", new DefaultTransactionAttribute());
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarTiposEntradaCasoDeUso(tipos));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarTiposEntrada.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarTiposEntrada) proxy.getObject();
    }
}

