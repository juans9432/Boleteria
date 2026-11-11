package com.boleteria.perfiles.infrastructure;

import javax.sql.DataSource;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles;
import com.boleteria.perfiles.application.port.out.Perfiles;
import com.boleteria.perfiles.application.usecase.GestionarPerfilesCasoDeUso;
import com.boleteria.perfiles.infrastructure.jdbc.PerfilesOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class PerfilesConfiguration {
    @Bean
    Perfiles perfiles(DataSource dataSource) {
        return new PerfilesOracle(dataSource);
    }

    @Bean
    GestionarPerfiles gestionarPerfiles(Perfiles perfiles, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("actualizar", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("eliminar", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarPerfilesCasoDeUso(perfiles));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarPerfiles.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarPerfiles) proxy.getObject();
    }
}





