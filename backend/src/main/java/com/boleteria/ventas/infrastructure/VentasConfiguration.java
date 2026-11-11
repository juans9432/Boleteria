package com.boleteria.ventas.infrastructure;

import java.time.Clock;
import javax.sql.DataSource;
import com.boleteria.reservas.application.port.out.Reservas;
import com.boleteria.ventas.application.port.in.ConfirmarVentas;
import com.boleteria.ventas.application.usecase.ConfirmarVentasCasoDeUso;
import com.boleteria.ventas.infrastructure.jdbc.EmisionOracle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class VentasConfiguration {
    @Bean ConfirmarVentas confirmarVentas(Reservas reservas, DataSource fuente, PlatformTransactionManager manager,
            com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos) {
        var atributos = new NameMatchTransactionAttributeSource();
        atributos.addTransactionalMethod("confirmar", new DefaultTransactionAttribute());
        var lectura = new DefaultTransactionAttribute();
        lectura.setReadOnly(true);
        lectura.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        atributos.addTransactionalMethod("cotizar", lectura);
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new ConfirmarVentasCasoDeUso(reservas, new EmisionOracle(fuente), Clock.systemUTC(), avisos));
        proxy.setProxyInterfaces(new Class<?>[]{ConfirmarVentas.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(atributos);
        proxy.afterPropertiesSet();
        return (ConfirmarVentas) proxy.getObject();
    }
}


