package com.boleteria.tiposprestador;

import java.util.Map;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class TiposPrestadorOracleTest {
    private static final String API = "/api/tipos-prestador";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final String correo = UUID.randomUUID() + "@tipos.invalid";
    private final String clave = UUID.randomUUID().toString();
    private final String nombre = "Prueba tipo " + UUID.randomUUID();
    private final String nit = UUID.randomUUID().toString().substring(0, 20);

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM PRESTADOR WHERE NIT = ?", nit);
        jdbc.update("DELETE FROM TIPO_PRESTADOR WHERE NOMBRE IN (?, ?, ?)", nombre, nombre + " nuevo", nombre + " otro");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void crudConOracleRespetaUniqueYClaveForanea() throws Exception {
        var sesion = login("ADMINISTRADOR");
        var token = token(sesion);
        long id = crear(sesion, token, nombre);
        long otro = crear(sesion, token, nombre + " otro");
        assertThat(otro).isNotEqualTo(id);
        String url = API + "/" + id;

        mvc.perform(get(API)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idTipoPrestador == " + id + ")].nombre").value(nombre));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(nombre));
        assertThat(nombreOracle(id)).isEqualTo(nombre);

        mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombre)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(
                        "Ya existe un tipo de prestador con ese nombre."));
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombre + " otro")))
                .andExpect(status().isConflict());
        assertThat(nombreOracle(id)).isEqualTo(nombre);
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombre + " nuevo")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idTipoPrestador").value(id));
        assertThat(nombreOracle(id)).isEqualTo(nombre + " nuevo");

        jdbc.update("""
                INSERT INTO PRESTADOR (NIT, NOMBRE, ID_MUNICIPIO, ID_TIPO_PRESTADOR)
                SELECT ?, 'Prueba tipo', MIN(ID_MUNICIPIO), ? FROM MUNICIPIO
                """, nit, id);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("El tipo tiene prestadores asociados."));
        assertThat(nombreOracle(id)).isEqualTo(nombre + " nuevo");
        jdbc.update("DELETE FROM PRESTADOR WHERE NIT = ?", nit);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TIPO_PRESTADOR WHERE ID_TIPO_PRESTADOR = ?",
                Long.class, id)).isZero();
    }

    @Test
    void exigeAdministradorCsrfYNombreValido() throws Exception {
        var anonima = new MockHttpSession();
        mvc.perform(post(API).session(anonima).header("X-CSRF-TOKEN", token(anonima))
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombre))).andExpect(status().isUnauthorized());
        var operador = login("OPERADOR");
        mvc.perform(post(API).session(operador).header("X-CSRF-TOKEN", token(operador))
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombre))).andExpect(status().isForbidden());
        jdbc.update("""
                UPDATE USUARIO_SISTEMA SET ID_ROL = (SELECT ID_ROL FROM ROL WHERE NOMBRE = 'ADMINISTRADOR')
                WHERE CORREO = ?
                """, correo);
        mvc.perform(get("/api/auth/sesion").session(operador)).andExpect(status().isUnauthorized());
        operador = autenticar();
        mvc.perform(post(API).session(operador).contentType(MediaType.APPLICATION_JSON).content(datos(nombre)))
                .andExpect(status().isForbidden());
        mvc.perform(post(API).session(operador).header("X-CSRF-TOKEN", token(operador))
                .contentType(MediaType.APPLICATION_JSON).content(datos(" "))).andExpect(status().isBadRequest());
    }

    private long crear(MockHttpSession sesion, String token, String nombreTipo) throws Exception {
        var respuesta = mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombreTipo)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idTipoPrestador").asLong();
        assertThat(respuesta.getHeader("Location")).isEqualTo(API + "/" + id);
        return id;
    }

    private String nombreOracle(long id) {
        return jdbc.queryForObject("SELECT NOMBRE FROM TIPO_PRESTADOR WHERE ID_TIPO_PRESTADOR = ?", String.class, id);
    }

    private String datos(String nombreTipo) throws Exception {
        return json.writeValueAsString(Map.of("nombre", nombreTipo));
    }

    private MockHttpSession login(String rol) throws Exception {
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL)
                SELECT ?, ?, 'ACTIVO', ID_ROL FROM ROL WHERE NOMBRE = ?
                """, correo, encoder.encode(clave), rol);
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

