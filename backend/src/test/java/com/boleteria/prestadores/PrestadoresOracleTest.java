package com.boleteria.prestadores;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class PrestadoresOracleTest {
    private static final String API = "/api/prestadores";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@prestadores.invalid";
    private final String nit = UUID.randomUUID().toString().substring(0, 18);
    private long municipio;
    private long tipo;

    @BeforeEach
    void catalogos() {
        municipio = jdbc.queryForObject("SELECT MIN(ID_MUNICIPIO) FROM MUNICIPIO", Long.class);
        tipo = jdbc.queryForObject("SELECT MIN(ID_TIPO_PRESTADOR) FROM TIPO_PRESTADOR", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
        jdbc.update("DELETE FROM PLAN WHERE NOMBRE = ?", nit);
        jdbc.update("DELETE FROM ATRACCION WHERE NOMBRE = ?", nit);
        jdbc.update("DELETE FROM PRESTADOR WHERE NIT IN (?, ?)", nit, nit + "2");
    }

    @Test
    void administradorCrudOracleUnicidadCatalogosYDependencias() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        var sesion = login("ADMINISTRADOR", null);
        var token = token(sesion);
        var alta = mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nit, "Prueba", municipio, tipo)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(alta.getContentAsString()).get("idPrestador").asLong();
        String url = API + "/" + id;
        assertThat(alta.getHeader("Location")).isEqualTo(url);
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(url).session(sesion)).andExpect(status().isOk()).andExpect(jsonPath("$.nit").value(nit));
        mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nit, "Duplicado", municipio, tipo)))
                .andExpect(status().isConflict());
        for (String invalido : new String[]{datos(nit, "Prueba", Long.MAX_VALUE, tipo),
                datos(nit, "Prueba", municipio, Long.MAX_VALUE)}) {
            mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", token)
                    .contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isBadRequest());
        }
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nit, "Actualizado", municipio, tipo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idPrestador").value(id));
        assertThat(jdbc.queryForObject("SELECT NOMBRE FROM PRESTADOR WHERE ID_PRESTADOR = ?", String.class, id))
                .isEqualTo("Actualizado");

        jdbc.update("INSERT INTO PLAN (NOMBRE, ESTADO, ID_PRESTADOR) VALUES (?, 'ACTIVO', ?)", nit, id);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token)).andExpect(status().isConflict());
        jdbc.update("DELETE FROM PLAN WHERE NOMBRE = ?", nit);
        jdbc.update("INSERT INTO ATRACCION (NOMBRE, ID_PRESTADOR) VALUES (?, ?)", nit, id);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token)).andExpect(status().isConflict());
        jdbc.update("DELETE FROM ATRACCION WHERE NOMBRE = ?", nit);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token)).andExpect(status().isNoContent());
        mvc.perform(get(url).session(sesion)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PRESTADOR WHERE ID_PRESTADOR = ?", Long.class, id)).isZero();
    }

    @Test
    void encargadoSoloListaConsultaYModificaElPropio() throws Exception {
        long propio = crearTemporal(nit);
        long ajeno = crearTemporal(nit + "2");
        var sesion = login("ENCARGADO_PRESTADOR", propio);
        var token = token(sesion);
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].idPrestador").value(propio));
        mvc.perform(get(API + "/" + propio).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + ajeno).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + propio).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nit, "Mi prestador", municipio, tipo)))
                .andExpect(status().isOk());
        mvc.perform(put(API + "/" + ajeno).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nit + "2", "Ajeno", municipio, tipo)))
                .andExpect(status().isForbidden());
        mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nit, "Prueba", municipio, tipo)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(API + "/" + propio).session(sesion).header("X-CSRF-TOKEN", token(sesion)))
                .andExpect(status().isForbidden());
        jdbc.update("""
                UPDATE USUARIO_SISTEMA SET ID_PRESTADOR = NULL,
                  ID_ROL = (SELECT ID_ROL FROM ROL WHERE NOMBRE = 'ADMINISTRADOR')
                WHERE CORREO = ?
                """, correo);
        mvc.perform(get("/api/auth/sesion").session(sesion)).andExpect(status().isUnauthorized());
        sesion = autenticar();
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL, ID_PRESTADOR)
                SELECT ?, 'PENDIENTE_CONFIGURACION', 'ACTIVO', ID_ROL, ? FROM ROL WHERE NOMBRE = 'ENCARGADO_PRESTADOR'
                """, "asociado-" + correo, propio);
        try {
            mvc.perform(delete(API + "/" + propio).session(sesion).header("X-CSRF-TOKEN", token(sesion)))
                    .andExpect(status().isConflict());
        } finally {
            jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", "asociado-" + correo);
        }
    }

    private long crearTemporal(String codigo) {
        jdbc.update("INSERT INTO PRESTADOR (NIT, NOMBRE, ID_MUNICIPIO, ID_TIPO_PRESTADOR) VALUES (?, 'Prueba', ?, ?)",
                codigo, municipio, tipo);
        return jdbc.queryForObject("SELECT ID_PRESTADOR FROM PRESTADOR WHERE NIT = ?", Long.class, codigo);
    }

    private String datos(String codigo, String nombre, long idMunicipio, long idTipo) throws Exception {
        return json.writeValueAsString(Map.of("nit", codigo, "nombre", nombre, "idMunicipio", idMunicipio, "idTipoPrestador", idTipo));
    }

    private MockHttpSession login(String rol, Long id) throws Exception {
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL, ID_PRESTADOR)
                SELECT ?, ?, 'ACTIVO', ID_ROL, ? FROM ROL WHERE NOMBRE = ?
                """, correo, encoder.encode(clave), new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NUMERIC, id), rol);
        return autenticar();
    }

    private MockHttpSession autenticar() throws Exception {
        var sesion = new MockHttpSession();
        return (MockHttpSession) mvc.perform(post("/api/auth/login").session(sesion)
                .header("X-CSRF-TOKEN", token(sesion)).param("correo", correo).param("clave", clave))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private String token(MockHttpSession sesion) throws Exception {
        var respuesta = mvc.perform(get("/api/auth/csrf").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}


