package com.boleteria.funciones.infrastructure;

import javax.sql.DataSource;
import com.boleteria.funciones.application.port.in.GestionarFunciones;
import com.boleteria.funciones.application.port.out.Funciones;
import com.boleteria.funciones.application.usecase.GestionarFuncionesCasoDeUso;
import com.boleteria.funciones.infrastructure.jdbc.FuncionesOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class FuncionesConfiguration {
    @Bean
    Funciones funciones(DataSource dataSource) {
        return new FuncionesOracle(dataSource);
    }

    @Bean
    GestionarFunciones gestionarFunciones(Funciones funciones, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("actualizar", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("eliminar", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarFuncionesCasoDeUso(funciones));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarFunciones.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarFunciones) proxy.getObject();
    }
}



