package com.boleteria.boletas.infrastructure;

import java.time.Clock;
import javax.sql.DataSource;
import com.boleteria.boletas.application.port.in.GestionarBoletas;
import com.boleteria.boletas.application.usecase.GestionarBoletasCasoDeUso;
import com.boleteria.boletas.infrastructure.jdbc.BoletasOracle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class BoletasConfiguration {
    @Bean Clock relojBoletas() { return Clock.systemUTC(); }
    @Bean GestionarBoletas gestionarBoletas(DataSource fuente, PlatformTransactionManager manager, Clock reloj) {
        var atributos = new NameMatchTransactionAttributeSource();
        atributos.addTransactionalMethod("usar", new DefaultTransactionAttribute());
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarBoletasCasoDeUso(new BoletasOracle(fuente), reloj));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarBoletas.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(atributos);
        proxy.afterPropertiesSet();
        return (GestionarBoletas) proxy.getObject();
    }
}


