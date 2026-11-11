package com.boleteria.usuarios.infrastructure;

import javax.sql.DataSource;
import com.boleteria.usuarios.application.port.in.AdministrarUsuarios;
import com.boleteria.usuarios.application.port.out.Usuarios;
import com.boleteria.usuarios.application.usecase.AdministrarUsuariosCasoDeUso;
import com.boleteria.usuarios.infrastructure.jdbc.UsuariosOracle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;

@Configuration(proxyBeanMethods = false)
class UsuariosConfiguration {
    @Bean
    Usuarios usuariosOracle(DataSource dataSource) { return new UsuariosOracle(dataSource); }

    @Bean
    AdministrarUsuarios administrarUsuarios(Usuarios usuarios, PasswordEncoder encoder, PlatformTransactionManager manager) {
        var transacciones = new NameMatchTransactionAttributeSource();
        transacciones.addTransactionalMethod("crear", new DefaultTransactionAttribute());
        transacciones.addTransactionalMethod("actualizar", new DefaultTransactionAttribute());
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new AdministrarUsuariosCasoDeUso(usuarios, encoder::encode));
        proxy.setProxyInterfaces(new Class<?>[]{AdministrarUsuarios.class});
        proxy.setTransactionManager(manager);
        proxy.setTransactionAttributeSource(transacciones);
        proxy.afterPropertiesSet();
        return (AdministrarUsuarios) proxy.getObject();
    }
}
