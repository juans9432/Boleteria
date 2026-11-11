package com.boleteria.combos.infrastructure;

import javax.sql.DataSource;
import com.boleteria.combos.application.port.in.GestionarCombos;
import com.boleteria.combos.application.port.out.Combos;
import com.boleteria.combos.application.usecase.GestionarCombosCasoDeUso;
import com.boleteria.combos.infrastructure.jdbc.CombosOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
class CombosConfiguration {
    @Bean
    Combos combos(DataSource dataSource) {
        return new CombosOracle(dataSource);
    }

    @Bean
    GestionarCombos gestionarCombos(Combos combos, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("actualizar", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("eliminar", new DefaultTransactionAttribute());

        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarCombosCasoDeUso(combos));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarCombos.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (GestionarCombos) proxy.getObject();
    }
}





