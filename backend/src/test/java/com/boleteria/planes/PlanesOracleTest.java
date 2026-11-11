package com.boleteria.planes;

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
class PlanesOracleTest {
    private static final String API = "/api/planes";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@planes.invalid";
    private final String marca = "Plan prueba " + UUID.randomUUID();
    private Long reserva;

    @AfterEach
    void limpiar() {
        if (reserva != null) jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA = ?", reserva);
        jdbc.update("DELETE FROM PLAN WHERE NOMBRE IN (?, ?, ?)", marca, marca + " editado", marca + " ajeno");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void administraPlanYConservaReferenciaHistorica() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        long prestador = prestadores().get(0);
        long id = crear(sesion, csrf, marca, prestador);
        String url = API + "/" + id;
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(url).session(sesion)).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(marca));
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, "ACTIVO", Long.MAX_VALUE))).andExpect(status().isBadRequest());
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        long perfil = jdbc.queryForObject("SELECT MIN(ID_PERFIL) FROM PERFIL_CLIENTE", Long.class);
        jdbc.update(conexion -> {
            var insert = conexion.prepareStatement("""
                    INSERT INTO RESERVA (ESTADO, ID_PERFIL, ID_PLAN)
                    VALUES ('CONFIRMADA', ?, ?)
                    """, new String[]{"ID_RESERVA"});
            insert.setLong(1, perfil);
            insert.setLong(2, id);
            return insert;
        }, key);
        reserva = key.getKey().longValue();
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isConflict());
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca + " editado", "INACTIVO", prestador))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ID_PLAN FROM RESERVA WHERE ID_RESERVA = ?", Long.class, reserva))
                .isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM PLAN WHERE ID_PLAN = ?", String.class, id))
                .isEqualTo("INACTIVO");
        jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA = ?", reserva);
        reserva = null;
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isNoContent());
        mvc.perform(get(url).session(sesion)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PLAN WHERE ID_PLAN = ?", Long.class, id)).isZero();
    }

    @Test
    void encargadoGestionaSusPlanesPeroNoLosAjenos() throws Exception {
        long propio = prestadores().get(0);
        long ajeno = prestadores().get(1);
        jdbc.update("INSERT INTO PLAN (NOMBRE, ESTADO, ID_PRESTADOR) VALUES (?, 'ACTIVO', ?)", marca + " ajeno", ajeno);
        long idAjeno = jdbc.queryForObject("SELECT ID_PLAN FROM PLAN WHERE NOMBRE = ?", Long.class, marca + " ajeno");
        var sesion = login("ENCARGADO_PRESTADOR", propio);
        var csrf = token(sesion);
        long id = crear(sesion, csrf, marca, propio);
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrestador != " + propio + ")]").isEmpty());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + idAjeno).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, "ACTIVO", ajeno))).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, "ACTIVO", ajeno))).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + idAjeno).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca, "ACTIVO", propio))).andExpect(status().isForbidden());
        mvc.perform(delete(API + "/" + idAjeno).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isForbidden());
        mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(marca + " editado", "ACTIVO", propio))).andExpect(status().isOk());
        mvc.perform(delete(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isNoContent());
    }

    private java.util.List<Long> prestadores() {
        return jdbc.queryForList("SELECT ID_PRESTADOR FROM PRESTADOR ORDER BY ID_PRESTADOR", Long.class);
    }

    private String datos(String nombre, String estado, long prestador) throws Exception {
        return json.writeValueAsString(Map.of("nombre", nombre, "estado", estado, "idPrestador", prestador));
    }

    private long crear(MockHttpSession sesion, String csrf, String nombre, long prestador) throws Exception {
        var respuesta = mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(nombre, "ACTIVO", prestador))).andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idPlan").asLong();
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


