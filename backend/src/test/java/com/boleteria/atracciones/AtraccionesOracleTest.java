package com.boleteria.atracciones;

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
class AtraccionesOracleTest {
    private static final String API = "/api/atracciones";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@atracciones.invalid";
    private final String marca = "Atraccion prueba " + UUID.randomUUID();
    private Long funcion;

    @AfterEach
    void limpiar() {
        if (funcion != null) jdbc.update("DELETE FROM FUNCION WHERE ID_FUNCION = ?", funcion);
        jdbc.update("DELETE FROM COMBO_ATRACCION WHERE ID_ATRACCION IN (SELECT ID_ATRACCION FROM ATRACCION WHERE NOMBRE = ?)", marca + " editado");
        jdbc.update("DELETE FROM ATRACCION WHERE NOMBRE IN (?, ?, ?)", marca, marca + " editado", marca + " ajeno");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void administradorVerificaCrudYProtegeFuncionesYCombos() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        long prestador = prestadores().get(0);
        long id = crear(sesion, csrf, marca, prestador);
        String url = API + "/" + id;
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(url).session(sesion)).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(marca));
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, Long.MAX_VALUE))).andExpect(status().isBadRequest());
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca + " editado", prestador))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT NOMBRE FROM ATRACCION WHERE ID_ATRACCION = ?", String.class, id))
                .isEqualTo(marca + " editado");

        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(conexion -> {
            var insert = conexion.prepareStatement("""
                    INSERT INTO FUNCION (FECHA, HORA_APERTURA, HORA_CIERRE, ESTADO, ID_ATRACCION)
                    VALUES (DATE '2030-01-01', '09:00', '10:00', 'PROGRAMADA', ?)
                    """, new String[]{"ID_FUNCION"});
            insert.setLong(1, id);
            return insert;
        }, key);
        funcion = key.getKey().longValue();
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT ID_ATRACCION FROM FUNCION WHERE ID_FUNCION = ?", Long.class, funcion))
                .isEqualTo(id);
        jdbc.update("DELETE FROM FUNCION WHERE ID_FUNCION = ?", funcion);
        funcion = null;

        long combo = jdbc.queryForObject("SELECT MIN(ID_COMBO) FROM COMBO", Long.class);
        jdbc.update("INSERT INTO COMBO_ATRACCION (ID_COMBO, ID_ATRACCION, PORCENTAJE_PRECIO) VALUES (?, ?, 10)", combo, id);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_ATRACCION WHERE ID_ATRACCION = ?", Long.class, id))
                .isEqualTo(1);
        jdbc.update("DELETE FROM COMBO_ATRACCION WHERE ID_COMBO = ? AND ID_ATRACCION = ?", combo, id);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isNoContent());
        mvc.perform(get(url).session(sesion)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ATRACCION WHERE ID_ATRACCION = ?", Long.class, id)).isZero();
    }

    @Test
    void encargadoGestionaSusAtraccionesPeroNoLosAjenos() throws Exception {
        long propio = prestadores().get(0);
        long ajeno = prestadores().get(1);
        jdbc.update("INSERT INTO ATRACCION (NOMBRE, ID_PRESTADOR) VALUES (?, ?)", marca + " ajeno", ajeno);
        long idAjeno = jdbc.queryForObject("SELECT ID_ATRACCION FROM ATRACCION WHERE NOMBRE = ?", Long.class, marca + " ajeno");
        var sesion = login("ENCARGADO_PRESTADOR", propio);
        var csrf = token(sesion);
        long id = crear(sesion, csrf, marca, propio);
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrestador != " + propio + ")]").isEmpty());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + idAjeno).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, ajeno))).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, ajeno))).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + idAjeno).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, propio))).andExpect(status().isForbidden());
        mvc.perform(delete(API + "/" + idAjeno).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca + " editado", propio))).andExpect(status().isOk());
        mvc.perform(delete(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isNoContent());
    }

    private java.util.List<Long> prestadores() {
        return jdbc.queryForList("SELECT ID_PRESTADOR FROM PRESTADOR ORDER BY ID_PRESTADOR", Long.class);
    }

    private String datos(String nombre, long prestador) throws Exception {
        return json.writeValueAsString(Map.of("nombre", nombre, "idPrestador", prestador));
    }

    private long crear(MockHttpSession sesion, String csrf, String nombre, long prestador) throws Exception {
        var respuesta = mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(nombre, prestador))).andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idAtraccion").asLong();
        assertThat(respuesta.getHeader("Location")).isEqualTo(API + "/" + id);
        return id;
    }

    private MockHttpSession login(String rol, Long id) throws Exception {
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL, ID_PRESTADOR)
                SELECT ?, ?, 'ACTIVO', ID_ROL, ? FROM ROL WHERE NOMBRE = ?
                """, correo, encoder.encode(clave), new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NUMERIC, id), rol);
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



