package com.boleteria.usuarios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.boleteria.usuarios.application.port.out.Usuarios;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class UsuariosOracleTest {
    private static final String API = "/api/usuarios";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired Usuarios usuarios;

    private final String marca = UUID.randomUUID().toString().substring(0, 16);
    private final String clave = UUID.randomUUID().toString();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM PERFIL_CLIENTE WHERE DOCUMENTO = ?", marca);
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO LIKE ?", marca + "%@usuarios.invalid");
    }

    @Test
    void administraCuentasRolesYProtegeVinculoConPerfil() throws Exception {
        cuenta("admin", "ADMINISTRADOR");
        cuenta("operador", "OPERADOR");
        var admin = login("admin", clave);
        var csrf = token(admin);
        mvc.perform(get("/api/roles").session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get(API).session(admin)).andExpect(status().isOk());
        mvc.perform(get(API).session(login("operador", clave))).andExpect(status().isForbidden());
        mvc.perform(post("/api/roles").session(admin).header("X-CSRF-TOKEN", csrf)).andExpect(status().isMethodNotAllowed());

        alta(admin, csrf, datos("nuevo", "ENCARGADO_PRESTADOR", "ACTIVO", null, clave)).andExpect(status().isBadRequest());
        alta(admin, csrf, datos("nuevo", "ENCARGADO_PRESTADOR", "ACTIVO", Long.MAX_VALUE, clave)).andExpect(status().isBadRequest());
        long id = crear(admin, csrf, datos("nuevo", "CLIENTE", "ACTIVO", null, clave));
        mvc.perform(get(API + "/" + id).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.clave").doesNotExist()).andExpect(jsonPath("$.claveHash").doesNotExist());
        assertThat(encoder.matches(clave, jdbc.queryForObject("SELECT CLAVE_HASH FROM USUARIO_SISTEMA WHERE ID_USUARIO = ?",
                String.class, id))).isTrue();
        alta(admin, csrf, datos("nuevo", "CLIENTE", "ACTIVO", null, clave)).andExpect(status().isConflict());
        jdbc.update("INSERT INTO PERFIL_CLIENTE (DOCUMENTO,NOMBRE,ID_USUARIO) VALUES (?, 'Temporal', ?)", marca, id);
        editar(admin, csrf, id, datos("nuevo", "OPERADOR", "ACTIVO", null, null)).andExpect(status().isConflict());
        editar(admin, csrf, id, datos("nuevo", "CLIENTE", "INACTIVO", null, null)).andExpect(status().isOk());
        loginRespuesta("nuevo", clave).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT ID_USUARIO FROM PERFIL_CLIENTE WHERE DOCUMENTO = ?", Long.class, marca))
                .isEqualTo(id);
    }

    @Test
    void revocaSesionPorCambioDePrestadorRolCorreoClaveYEstado() throws Exception {
        cuenta("admin", "ADMINISTRADOR");
        var admin = login("admin", clave);
        var csrf = token(admin);
        var prestadores = jdbc.queryForList("SELECT ID_PRESTADOR FROM PRESTADOR ORDER BY ID_PRESTADOR", Long.class);
        long propio = prestadores.get(0), otro = prestadores.get(1);
        long id = crear(admin, csrf, datos("destino", "ENCARGADO_PRESTADOR", "ACTIVO", propio, clave));
        var vieja = login("destino", clave);
        mvc.perform(get("/api/auth/prestadores/" + propio + "/acceso").session(vieja)).andExpect(status().isNoContent());
        editar(admin, csrf, id, datos("destino", "ENCARGADO_PRESTADOR", "ACTIVO", otro, null)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/prestadores/" + propio + "/acceso").session(vieja)).andExpect(status().isUnauthorized());
        var reasignada = login("destino", clave);
        mvc.perform(get("/api/auth/prestadores/" + otro + "/acceso").session(reasignada)).andExpect(status().isNoContent());
        editar(admin, csrf, id, datos("destino", "OPERADOR", "ACTIVO", null, null)).andExpect(status().isOk());
        revocada(reasignada);
        var conRol = login("destino", clave);
        editar(admin, csrf, id, datos("renombrado", "OPERADOR", "ACTIVO", null, null)).andExpect(status().isOk());
        revocada(conRol);
        var conCorreo = login("renombrado", clave);
        String nueva = UUID.randomUUID().toString();
        editar(admin, csrf, id, datos("renombrado", "OPERADOR", "ACTIVO", null, nueva)).andExpect(status().isOk());
        revocada(conCorreo);
        loginRespuesta("renombrado", clave).andExpect(status().isUnauthorized());
        var conClave = login("renombrado", nueva);
        editar(admin, csrf, id, datos("renombrado", "OPERADOR", "INACTIVO", null, null)).andExpect(status().isOk());
        revocada(conClave);
    }

    @Test
    void serializaDosDesactivacionesConcurrentes() throws Exception {
        long anteriores = usuarios.administradoresUtilizables();
        long uno = cuenta("uno", "ADMINISTRADOR"), dos = cuenta("dos", "ADMINISTRADOR");
        var sesionUno = login("uno", clave);
        var sesionDos = login("dos", clave);
        var csrfUno = token(sesionUno);
        var csrfDos = token(sesionDos);
        var inicio = new CountDownLatch(1);
        var ejecutor = Executors.newFixedThreadPool(2);
        try {
            var a = ejecutor.submit(() -> {
                inicio.await();
                return editar(sesionUno, csrfUno, uno, datos("uno", "ADMINISTRADOR", "INACTIVO", null, null))
                        .andReturn().getResponse().getStatus();
            });
            var b = ejecutor.submit(() -> {
                inicio.await();
                return editar(sesionDos, csrfDos, dos, datos("dos", "ADMINISTRADOR", "INACTIVO", null, null))
                        .andReturn().getResponse().getStatus();
            });
            inicio.countDown();
            var estados = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            if (anteriores == 0) assertThat(estados).containsExactlyInAnyOrder(200, 409);
            else assertThat(estados).containsExactly(200, 200);
            assertThat(usuarios.administradoresUtilizables()).isEqualTo(Math.max(anteriores, 1));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private void revocada(MockHttpSession sesion) throws Exception {
        mvc.perform(get("/api/auth/sesion").session(sesion)).andExpect(status().isUnauthorized());
    }

    private long cuenta(String sufijo, String rol) {
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL)
                SELECT ?, ?, 'ACTIVO', ID_ROL FROM ROL WHERE NOMBRE = ?
                """, correo(sufijo), encoder.encode(clave), rol);
        return jdbc.queryForObject("SELECT ID_USUARIO FROM USUARIO_SISTEMA WHERE CORREO = ?", Long.class, correo(sufijo));
    }

    private String correo(String sufijo) { return marca + sufijo + "@usuarios.invalid"; }

    private String datos(String sufijo, String rol, String estado, Long prestador, String contrasena) throws Exception {
        Map<String, Object> datos = new HashMap<>();
        datos.put("correo", correo(sufijo));
        datos.put("rol", rol);
        datos.put("estado", estado);
        datos.put("idPrestador", prestador);
        datos.put("clave", contrasena);
        return json.writeValueAsString(datos);
    }

    private ResultActions alta(MockHttpSession sesion, String csrf, String cuerpo) throws Exception {
        return mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private long crear(MockHttpSession sesion, String csrf, String cuerpo) throws Exception {
        var respuesta = alta(sesion, csrf, cuerpo).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(respuesta.getContentAsString()).get("idUsuario").asLong();
    }

    private ResultActions editar(MockHttpSession sesion, String csrf, long id, String cuerpo) throws Exception {
        return mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private MockHttpSession login(String sufijo, String clave) throws Exception {
        return (MockHttpSession) loginRespuesta(sufijo, clave).andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions loginRespuesta(String sufijo, String clave) throws Exception {
        var sesion = new MockHttpSession();
        return mvc.perform(post("/api/auth/login").session(sesion).header("X-CSRF-TOKEN", token(sesion))
                .param("correo", correo(sufijo)).param("clave", clave));
    }

    private String token(MockHttpSession sesion) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}

