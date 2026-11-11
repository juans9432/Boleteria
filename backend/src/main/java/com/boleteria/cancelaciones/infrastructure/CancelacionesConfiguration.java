package com.boleteria.cancelaciones.infrastructure;

import javax.sql.DataSource;
import com.boleteria.cancelaciones.application.port.in.GestionarCancelaciones;
import com.boleteria.cancelaciones.application.usecase.GestionarCancelacionesCasoDeUso;
import com.boleteria.cancelaciones.infrastructure.jdbc.CancelacionesOracle;
import com.boleteria.reservas.application.port.out.Reservas;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CancelacionesConfiguration {
    @Bean GestionarCancelaciones gestionarCancelaciones(Reservas reservas, DataSource fuente,
                                                        PlatformTransactionManager manager,
                                                        com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos) {
        var atributos = new NameMatchTransactionAttributeSource();
        atributos.addTransactionalMethod("*", new DefaultTransactionAttribute());
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new GestionarCancelacionesCasoDeUso(reservas, new CancelacionesOracle(fuente), avisos));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarCancelaciones.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(atributos);
        proxy.afterPropertiesSet();
        return (GestionarCancelaciones) proxy.getObject();
    }
}


