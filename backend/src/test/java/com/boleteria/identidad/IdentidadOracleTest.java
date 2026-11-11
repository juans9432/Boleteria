package com.boleteria.identidad;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class IdentidadOracleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final String correo = UUID.randomUUID() + "@prueba.invalid";
    private final String clave = UUID.randomUUID().toString();
    private long propio;
    private long ajeno;

    @BeforeEach
    void crearCuenta() {
        var prestadores = jdbc.queryForList("SELECT ID_PRESTADOR FROM PRESTADOR ORDER BY ID_PRESTADOR",
                Long.class);
        propio = prestadores.get(0);
        ajeno = prestadores.get(1);
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL, ID_PRESTADOR)
                SELECT ?, ?, 'ACTIVO', ID_ROL, ? FROM ROL WHERE NOMBRE = 'ENCARGADO_PRESTADOR'
                """, correo, encoder.encode(clave), propio);
    }

    @AfterEach
    void limpiarCuenta() {
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void autenticaConOracleLimitaPrestadorYCierraSesion() throws Exception {
        mvc.perform(get("/api/auth/sesion")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("correo", correo).param("clave", clave))
                .andExpect(status().isForbidden());

        MockHttpSession sesion = (MockHttpSession) login(clave).andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/auth/sesion").session(sesion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ENCARGADO_PRESTADOR"))
                .andExpect(jsonPath("$.idPrestador").value(propio));
        mvc.perform(get("/api/auth/prestadores/{id}/acceso", propio).session(sesion))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/prestadores/{id}/acceso", ajeno).session(sesion))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/logout").session(sesion).header("X-CSRF-TOKEN", token(sesion)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/sesion")).andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaClaveErroneaCuentaInactivaYEncargadoSinPrestador() throws Exception {
        login("incorrecta").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE USUARIO_SISTEMA SET ESTADO = 'INACTIVO' WHERE CORREO = ?", correo);
        login(clave).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE USUARIO_SISTEMA SET ESTADO = 'ACTIVO', ID_PRESTADOR = NULL WHERE CORREO = ?", correo);
        login(clave).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String claveIngresada) throws Exception {
        var sesion = new MockHttpSession();
        return mvc.perform(post("/api/auth/login").session(sesion).header("X-CSRF-TOKEN", token(sesion))
                .param("correo", correo).param("clave", claveIngresada));
    }

    private String token(MockHttpSession sesion) throws Exception {
        var respuesta = mvc.perform(get("/api/auth/csrf").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
