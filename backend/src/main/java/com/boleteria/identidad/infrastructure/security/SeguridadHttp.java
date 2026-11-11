package com.boleteria.identidad.infrastructure.security;

import java.util.regex.Pattern;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import com.boleteria.identidad.domain.Rol;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SeguridadHttp {
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Bean
    SesionesVigentes sesionesVigentes(ConsultarIdentidad identidad) {
        return new SesionesVigentes(identidad);
    }

    @Bean
    FilterRegistrationBean<SesionesVigentes> registroSesiones(SesionesVigentes sesiones) {
        var registro = new FilterRegistrationBean<>(sesiones);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    UserDetailsService usuarios(ConsultarIdentidad identidad, SesionesVigentes sesiones) {
        return correo -> identidad.buscarCredencial(correo)
                .filter(credencial -> credencial.cuenta().habilitada())
                .filter(credencial -> BCRYPT.matcher(credencial.hash()).matches())
                .map(sesiones::principal)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, ConsultarIdentidad identidad, SesionesVigentes sesiones) throws Exception {
        return http.addFilterBefore(sesiones, AuthorizationFilter.class).authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.GET, "/api/municipios", "/api/municipios/*", "/api/tipos-prestador", "/api/tipos-prestador/*", "/api/tipos-entrada", "/api/tipos-entrada/*", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/sesion",
                                "/api/auth/prestadores/*/acceso").authenticated()
                        .requestMatchers("/api/municipios", "/api/municipios/*", "/api/tipos-prestador", "/api/tipos-prestador/*", "/api/tipos-entrada", "/api/tipos-entrada/*", "/api/combos", "/api/combos/*", "/api/usuarios", "/api/usuarios/*", "/api/roles")
                        .access((authentication, context) -> {
                            var actual = authentication.get();
                            return new AuthorizationDecision(actual != null && actual.isAuthenticated()
                                    && !(actual instanceof AnonymousAuthenticationToken)
                                    && identidad.consultarCuenta(actual.getName())
                                            .filter(cuenta -> cuenta.rol() == Rol.ADMINISTRADOR).isPresent());
                        })
                        .requestMatchers("/api/prestadores", "/api/prestadores/*", "/api/planes", "/api/planes/*", "/api/atracciones", "/api/atracciones/*", "/api/funciones", "/api/funciones/*", "/api/zonas", "/api/zonas/*", "/api/perfiles", "/api/perfiles/*", "/api/reservas", "/api/reservas/*", "/api/reservas/*/cancelar", "/api/reservas/*/cotizacion", "/api/reservas/*/confirmar", "/api/boletas", "/api/boletas/*", "/api/boletas/*/usar", "/api/boletas/*/anular", "/api/boletas/*/anular-combo", "/api/reservas/*/cancelar-confirmada", "/api/funciones/*/cancelar", "/api/notificaciones", "/api/notificaciones/*", "/api/notificaciones/*/leer").authenticated()
                        .anyRequest().denyAll())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errores -> errores.authenticationEntryPoint(
                        (request, response, error) -> response.setStatus(401)))
                .formLogin(login -> login.loginPage("/api/auth/login").loginProcessingUrl("/api/auth/login")
                        .usernameParameter("correo").passwordParameter("clave")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, error) -> response.setStatus(401)))
                .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .build();
    }
}





