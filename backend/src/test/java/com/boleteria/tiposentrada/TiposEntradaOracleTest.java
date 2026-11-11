package com.boleteria.tiposentrada;

import java.math.BigDecimal;
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
class TiposEntradaOracleTest {
    private static final String API = "/api/tipos-entrada";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@entradas.invalid";
    private final String codigo = UUID.randomUUID().toString();
    private final String nombre = "Entrada " + UUID.randomUUID().toString().substring(0, 24);

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM BOLETA WHERE CODIGO = ?", codigo);
        jdbc.update("DELETE FROM TIPO_ENTRADA WHERE NOMBRE IN (?, ?)", nombre, nombre + " nueva");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void crudConservaPrecioPagadoYBloqueaUnaBoletaAnulada() throws Exception {
        var sesion = login("ADMINISTRADOR");
        var csrf = token(sesion);
        var respuesta = alta(sesion, csrf, datos(nombre, "10.25", "S")).andExpect(status().isCreated())
                .andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idTipoEntrada").asLong();
        String url = API + "/" + id;
        assertThat(respuesta.getHeader("Location")).isEqualTo(url);
        mvc.perform(get(API)).andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.requiereAdulto").value("S"));
        alta(sesion, csrf, datos(nombre, "10", "N")).andExpect(status().isConflict());
        long reserva = jdbc.queryForObject("SELECT MIN(ID_RESERVA) FROM RESERVA", Long.class);
        long zona = jdbc.queryForObject("SELECT MIN(ID_ZONA) FROM ZONA", Long.class);
        jdbc.update("""
                INSERT INTO BOLETA (CODIGO, ESTADO, PRECIO_PAGADO, ID_RESERVA, ID_ZONA, ID_TIPO_ENTRADA)
                VALUES (?, 'ANULADA', 1000, ?, ?, ?)
                """, codigo, reserva, zona, id);

        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(nombre + " nueva", "25.50", "N"))).andExpect(status().isConflict());
        mvc.perform(put(url).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(datos(nombre + " nueva", "25.50", "S"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT PORCENTAJE_DESCUENTO FROM TIPO_ENTRADA WHERE ID_TIPO_ENTRADA = ?",
                BigDecimal.class, id)).isEqualByComparingTo("25.50");
        assertThat(jdbc.queryForObject("SELECT PRECIO_PAGADO FROM BOLETA WHERE CODIGO = ?", BigDecimal.class, codigo))
                .isEqualByComparingTo("1000");
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT ID_TIPO_ENTRADA FROM BOLETA WHERE CODIGO = ?", Long.class, codigo)).isEqualTo(id);
        jdbc.update("DELETE FROM BOLETA WHERE CODIGO = ?", codigo);
        mvc.perform(delete(url).session(sesion).header("X-CSRF-TOKEN", csrf)).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void soloAdministradorEscribeYValidaElContenido() throws Exception {
        var anonima = new MockHttpSession();
        alta(anonima, token(anonima), datos(nombre, "10", "N")).andExpect(status().isUnauthorized());
        var operador = login("OPERADOR");
        alta(operador, token(operador), datos(nombre, "10", "N")).andExpect(status().isForbidden());
        jdbc.update("""
                UPDATE USUARIO_SISTEMA SET ID_ROL = (SELECT ID_ROL FROM ROL WHERE NOMBRE = 'ADMINISTRADOR')
                WHERE CORREO = ?
                """, correo);
        mvc.perform(get("/api/auth/sesion").session(operador)).andExpect(status().isUnauthorized());
        operador = autenticar();
        mvc.perform(post(API).session(operador).contentType(MediaType.APPLICATION_JSON)
                .content(datos(nombre, "10", "N"))).andExpect(status().isForbidden());
        var csrf = token(operador);
        alta(operador, csrf, datos(" ", "10", "N")).andExpect(status().isBadRequest());
        alta(operador, csrf, datos(nombre, "101", "N")).andExpect(status().isBadRequest());
        alta(operador, csrf, datos(nombre, "10", "X")).andExpect(status().isBadRequest());
    }

    private String datos(String n, String descuento, String requiere) throws Exception {
        return json.writeValueAsString(Map.of("nombre", n, "porcentajeDescuento", new BigDecimal(descuento),
                "requiereAdulto", requiere));
    }

    private ResultActions alta(MockHttpSession sesion, String csrf, String datos) throws Exception {
        return mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos));
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



