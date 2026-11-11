package com.boleteria.identidad.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.boleteria.identidad.application.port.in.ConsultarIdentidad;
import com.boleteria.identidad.application.port.out.Cuentas.Credencial;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.filter.OncePerRequestFilter;

public class SesionesVigentes extends OncePerRequestFilter {
    private final ConsultarIdentidad identidad;
    private final ConcurrentHashMap<Long, Long> revocaciones = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    SesionesVigentes(ConsultarIdentidad identidad) { this.identidad = identidad; }

    public void revocar(long idUsuario) {
        revocaciones.merge(idUsuario, secuencia.incrementAndGet(), Math::max);
    }

    User principal(Credencial credencial) {
        return new PrincipalCuenta(credencial, huella(credencial));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        boolean vigente;
        try {
            vigente = autenticacion.getPrincipal() instanceof PrincipalCuenta anterior
                    && identidad.buscarCredencial(anterior.getUsername())
                            .filter(c -> c.cuenta().habilitada())
                            .map(this::huella).filter(anterior.huella::equals).isPresent();
        } catch (org.springframework.dao.DataAccessException error) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        if (!vigente) {
            var sesion = request.getSession(false);
            if (sesion != null) sesion.invalidate();
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private String huella(Credencial credencial) {
        var cuenta = credencial.cuenta();
        long revision = revocaciones.getOrDefault(cuenta.id(), 0L);
        String datos = cuenta.id() + "|" + cuenta.correo() + "|" + cuenta.rol() + "|" + cuenta.activa()
                + "|" + cuenta.idPrestador() + "|" + revision + "|" + credencial.hash();
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("No está disponible SHA-256.", error);
        }
    }

    private static class PrincipalCuenta extends User {
        private final String huella;

        PrincipalCuenta(Credencial credencial, String huella) {
            super(credencial.cuenta().correo(), credencial.hash(),
                    AuthorityUtils.createAuthorityList("ROLE_" + credencial.cuenta().rol().name()));
            this.huella = huella;
        }
    }
}
