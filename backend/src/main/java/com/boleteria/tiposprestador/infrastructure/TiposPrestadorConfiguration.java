package com.boleteria.tiposprestador.infrastructure;

import javax.sql.DataSource;
import com.boleteria.tiposprestador.application.port.in.GestionarTiposPrestador;
import com.boleteria.tiposprestador.application.port.out.TiposPrestador;
import com.boleteria.tiposprestador.application.usecase.GestionarTiposPrestadorCasoDeUso;
import com.boleteria.tiposprestador.infrastructure.jdbc.TiposPrestadorOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class TiposPrestadorConfiguration {
    @Bean
    TiposPrestador tiposPrestador(DataSource dataSource) {
        return new TiposPrestadorOracle(dataSource);
    }

    @Bean
    GestionarTiposPrestador gestionarTiposPrestador(TiposPrestador tipos, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarTiposPrestadorCasoDeUso(tipos));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarTiposPrestador.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarTiposPrestador) proxy.getObject();
    }
}
