package com.boleteria.planes.infrastructure;

import javax.sql.DataSource;
import com.boleteria.planes.application.port.in.GestionarPlanes;
import com.boleteria.planes.application.port.out.Planes;
import com.boleteria.planes.application.usecase.GestionarPlanesCasoDeUso;
import com.boleteria.planes.infrastructure.jdbc.PlanesOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class PlanesConfiguration {
    @Bean
    Planes planes(DataSource dataSource) {
        return new PlanesOracle(dataSource);
    }

    @Bean
    GestionarPlanes gestionarPlanes(Planes planes, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("eliminar", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarPlanesCasoDeUso(planes));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarPlanes.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarPlanes) proxy.getObject();
    }
}


