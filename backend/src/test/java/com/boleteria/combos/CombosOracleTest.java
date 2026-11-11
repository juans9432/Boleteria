package com.boleteria.combos;

import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class CombosOracleTest {
    private static final String API = "/api/combos";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@combos.invalid";
    private final String codigo = UUID.randomUUID().toString();
    private final String nombre = "Combo prueba " + UUID.randomUUID();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM BOLETA WHERE CODIGO = ?", codigo);
        jdbc.update("DELETE FROM COMBO WHERE NOMBRE IN (?, ?)", nombre, nombre + " editado");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void conservaBorradorYRevierteTodasLasEscriturasAlFallarUnaRelacion() throws Exception {
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        long a = jdbc.queryForObject("SELECT MIN(ID_ATRACCION) FROM ATRACCION", Long.class);
        var relacion = relacion(a, "40");
        var inexistente = relacion(Long.MAX_VALUE, "60");
        alta(sesion, csrf, datos(nombre, 1000, "INACTIVO", List.of(relacion, inexistente)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM COMBO WHERE NOMBRE = ?", Long.class, nombre)).isZero();
        alta(sesion, csrf, datos(nombre, 1000, "INACTIVO", List.of(relacion, relacion)))
                .andExpect(status().isBadRequest());
        long id = crear(sesion, csrf, List.of(relacion));
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.atracciones[0].porcentaje").value(40));
        editar(sesion, csrf, id, datos(nombre, 1000, "ACTIVO", List.of(relacion))).andExpect(status().isBadRequest());
        editar(sesion, csrf, id, datos(nombre + " editado", 2000, "INACTIVO", List.of(relacion(a, "25"), inexistente)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT NOMBRE FROM COMBO WHERE ID_COMBO = ?", String.class, id)).isEqualTo(nombre);
        assertThat(jdbc.queryForObject("SELECT PRECIO FROM COMBO WHERE ID_COMBO = ?", BigDecimal.class, id))
                .isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT PORCENTAJE_PRECIO FROM COMBO_ATRACCION WHERE ID_COMBO = ?",
                BigDecimal.class, id)).isEqualByComparingTo("40");
        borrar(sesion, csrf, id).andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isNotFound());
    }

    @Test
    void activaInactivaYProtegePrecioYReferenciaHistoricos() throws Exception {
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        var ids = jdbc.queryForList("SELECT ID_ATRACCION FROM ATRACCION ORDER BY ID_ATRACCION", Long.class);
        var relaciones = List.of(relacion(ids.get(0), "33.33"), relacion(ids.get(ids.size() - 1), "66.67"));
        long id = crear(sesion, csrf, List.of());
        editar(sesion, csrf, id, datos(nombre, 1000, "ACTIVO", relaciones)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        editar(sesion, csrf, id, datos(nombre, 1000, "ACTIVO", List.of(relacion(ids.get(0), "100"))))
                .andExpect(status().isConflict());
        long reserva = jdbc.queryForObject("SELECT MIN(ID_RESERVA) FROM RESERVA", Long.class);
        long zona = jdbc.queryForObject("SELECT MIN(ID_ZONA) FROM ZONA", Long.class);
        long tipo = jdbc.queryForObject("SELECT MIN(ID_TIPO_ENTRADA) FROM TIPO_ENTRADA", Long.class);
        jdbc.update("""
                INSERT INTO BOLETA (CODIGO, ESTADO, PRECIO_PAGADO, ID_RESERVA, ID_ZONA, ID_TIPO_ENTRADA, ID_COMBO)
                VALUES (?, 'ANULADA', 450, ?, ?, ?, ?)
                """, codigo, reserva, zona, tipo, id);
        borrar(sesion, csrf, id).andExpect(status().isConflict());
        editar(sesion, csrf, id, datos(nombre + " editado", 2500, "INACTIVO", relaciones)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT PRECIO_PAGADO FROM BOLETA WHERE CODIGO = ?", BigDecimal.class, codigo))
                .isEqualByComparingTo("450");
        assertThat(jdbc.queryForObject("SELECT ID_COMBO FROM BOLETA WHERE CODIGO = ?", Long.class, codigo)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM COMBO WHERE ID_COMBO = ?", String.class, id)).isEqualTo("INACTIVO");
        borrar(sesion, csrf, id).andExpect(status().isConflict());
    }

    @Test
    void soloAdministradorPuedeGestionarCatalogoGlobal() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        long prestador = jdbc.queryForObject("SELECT MIN(ID_PRESTADOR) FROM PRESTADOR", Long.class);
        var sesion = login("ENCARGADO_PRESTADOR", prestador);
        var csrf = token(sesion);
        mvc.perform(get(API).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/1").session(sesion)).andExpect(status().isForbidden());
        alta(sesion, csrf, datos(nombre, 1000, "INACTIVO", List.of())).andExpect(status().isForbidden());
        editar(sesion, csrf, 1, datos(nombre, 1000, "INACTIVO", List.of())).andExpect(status().isForbidden());
        borrar(sesion, csrf, 1).andExpect(status().isForbidden());
    }

    private long crear(MockHttpSession sesion, String csrf, List<Map<String, Object>> relaciones) throws Exception {
        var respuesta = alta(sesion, csrf, datos(nombre, 1000, "INACTIVO", relaciones))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idCombo").asLong();
        assertThat(respuesta.getHeader("Location")).isEqualTo(API + "/" + id);
        return id;
    }

    private Map<String, Object> relacion(long id, String porcentaje) {
        return Map.of("idAtraccion", id, "porcentaje", new BigDecimal(porcentaje));
    }

    private String datos(String n, int precio, String estado, List<Map<String, Object>> relaciones) throws Exception {
        return json.writeValueAsString(Map.of("nombre", n, "precio", precio, "diasVigencia", 2,
                "estado", estado, "atracciones", relaciones));
    }

    private ResultActions alta(MockHttpSession s, String csrf, String datos) throws Exception {
        return mvc.perform(post(API).session(s).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos));
    }

    private ResultActions editar(MockHttpSession s, String csrf, long id, String datos) throws Exception {
        return mvc.perform(put(API + "/" + id).session(s).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos));
    }

    private ResultActions borrar(MockHttpSession s, String csrf, long id) throws Exception {
        return mvc.perform(delete(API + "/" + id).session(s).header("X-CSRF-TOKEN", csrf));
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







