package com.boleteria.reservas.infrastructure;

import java.time.Clock;
import javax.sql.DataSource;
import com.boleteria.reservas.application.port.in.GestionarReservas;
import com.boleteria.reservas.application.usecase.ReservasCasoDeUso;
import com.boleteria.reservas.infrastructure.jdbc.ReservasOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableScheduling
class ReservasConfiguration {
    @Bean ReservasOracle reservasOracle(DataSource fuente) { return new ReservasOracle(fuente); }
    @Bean GestionarReservas gestionarReservas(ReservasOracle datos, PlatformTransactionManager manager,
            com.boleteria.notificaciones.application.port.in.PublicarAvisos avisos) {
        var atributos = new NameMatchTransactionAttributeSource();
        for (String operacion : new String[]{"crear","editar","cancelar","expirar"}) {
            atributos.addTransactionalMethod(operacion, new DefaultTransactionAttribute());
        }
        var lectura = new DefaultTransactionAttribute();
        lectura.setReadOnly(true);
        lectura.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE);
        for (String operacion : new String[]{"listar","consultar","catalogos"}) {
            atributos.addTransactionalMethod(operacion, lectura);
        }
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new ReservasCasoDeUso(datos, Clock.systemUTC(), avisos));
        proxy.setProxyInterfaces(new Class<?>[]{GestionarReservas.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(atributos);
        proxy.afterPropertiesSet();
        return (GestionarReservas) proxy.getObject();
    }
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "boleteria.reservas.expiracion-activa", matchIfMissing = true)
    Expirador expirador(GestionarReservas reservas) { return new Expirador(reservas); }

    record Expirador(GestionarReservas reservas) {
        @Scheduled(initialDelayString = "1000", fixedDelayString = "${boleteria.reservas.intervalo-ms:30000}")
        public void expirar() { reservas.expirar(); }
    }
}




