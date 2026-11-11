package com.boleteria.municipios;

import java.sql.Types;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class MunicipiosCrudOracleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final String correo = UUID.randomUUID() + "@municipios.invalid";
    private final String clave = UUID.randomUUID().toString();
    private final String nombre = "Prueba " + UUID.randomUUID();
    private final String nit = UUID.randomUUID().toString().substring(0, 20);

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM PRESTADOR WHERE NIT = ?", nit);
        jdbc.update("DELETE FROM MUNICIPIO WHERE NOMBRE IN (?, ?)", nombre, nombre + " actualizado");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void administraEnOracleYConservaElMunicipioReferenciado() throws Exception {
        var sesion = login("ADMINISTRADOR", null);
        String token = token(sesion);
        var creado = mvc.perform(post("/api/municipios").session(sesion).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(datos(nombre)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value(nombre))
                .andReturn().getResponse();
        long id = json.readTree(creado.getContentAsString()).get("idMunicipio").asLong();
        String url = "/api/municipios/" + id;
        assertThat(creado.getHeader("Location")).isEqualTo(url);
        assertThat(nombreOracle(id)).isEqualTo(nombre);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(nombre));
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(datos(nombre + " actualizado")))
                .andExpect(status().isOk());
        assertThat(nombreOracle(id)).isEqualTo(nombre + " actualizado");

        jdbc.update("""
                INSERT INTO PRESTADOR (NIT, NOMBRE, ID_MUNICIPIO, ID_TIPO_PRESTADOR)
                SELECT ?, 'Prueba municipio', ?, MIN(ID_TIPO_PRESTADOR) FROM TIPO_PRESTADOR
                """, nit, id);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token))
                .andExpect(status().isConflict());
        assertThat(nombreOracle(id)).isEqualTo(nombre + " actualizado");

        jdbc.update("DELETE FROM PRESTADOR WHERE NIT = ?", nit);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MUNICIPIO WHERE ID_MUNICIPIO = ?", Long.class, id))
                .isZero();
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(datos(nombre)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validaCsrfSesionRolYCampos() throws Exception {
        var anonima = new MockHttpSession();
        mvc.perform(post("/api/municipios").session(anonima).header("X-CSRF-TOKEN", token(anonima))
                        .contentType(MediaType.APPLICATION_JSON).content(datos(nombre)))
                .andExpect(status().isUnauthorized());
        Long prestador = jdbc.queryForObject("SELECT MIN(ID_PRESTADOR) FROM PRESTADOR", Long.class);
        var sesion = login("ENCARGADO_PRESTADOR", prestador);
        mvc.perform(post("/api/municipios").session(sesion).header("X-CSRF-TOKEN", token(sesion))
                        .contentType(MediaType.APPLICATION_JSON).content(datos(nombre)))
                .andExpect(status().isForbidden());
        jdbc.update("""
                UPDATE USUARIO_SISTEMA SET ID_PRESTADOR = NULL,
                    ID_ROL = (SELECT ID_ROL FROM ROL WHERE NOMBRE = 'ADMINISTRADOR')
                WHERE CORREO = ?
                """, correo);
        mvc.perform(get("/api/auth/sesion").session(sesion)).andExpect(status().isUnauthorized());
        sesion = autenticar();
        mvc.perform(post("/api/municipios").session(sesion).contentType(MediaType.APPLICATION_JSON)
                        .content(datos(nombre))).andExpect(status().isForbidden());
        mvc.perform(post("/api/municipios").session(sesion).header("X-CSRF-TOKEN", token(sesion))
                        .contentType(MediaType.APPLICATION_JSON).content(datos(" ")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MUNICIPIO WHERE NOMBRE = ?", Long.class, nombre))
                .isZero();
    }

    private String nombreOracle(long id) {
        return jdbc.queryForObject("SELECT NOMBRE FROM MUNICIPIO WHERE ID_MUNICIPIO = ?", String.class, id);
    }

    private String datos(String nombre) throws Exception {
        return json.writeValueAsString(java.util.Map.of("nombre", nombre, "departamento", "Quindío"));
    }

    private MockHttpSession login(String rol, Long prestador) throws Exception {
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL, ID_PRESTADOR)
                SELECT ?, ?, 'ACTIVO', ID_ROL, ? FROM ROL WHERE NOMBRE = ?
                """, correo, encoder.encode(clave), new SqlParameterValue(Types.NUMERIC, prestador), rol);
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


