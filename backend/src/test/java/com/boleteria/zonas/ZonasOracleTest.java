package com.boleteria.zonas;

import java.math.BigDecimal;
import java.util.ArrayList;
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
class ZonasOracleTest {
    private static final String API = "/api/zonas";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@zonas.invalid";
    private final String codigo = UUID.randomUUID().toString().substring(0, 32);
    private final java.util.List<Long> funciones = new ArrayList<>();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM BOLETA WHERE CODIGO IN (?, ?, ?)", codigo + "1", codigo + "2", codigo + "3");
        for (long id : funciones) {
            jdbc.update("DELETE FROM ZONA WHERE ID_FUNCION = ?", id);
            jdbc.update("DELETE FROM FUNCION WHERE ID_FUNCION = ?", id);
        }
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void crudProtegeAforoPrecioHistoricoYBoletasAnuladas() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        long funcion = funcion(prestadores().get(0), "PROGRAMADA");
        alta(sesion, csrf, Long.MAX_VALUE).andExpect(status().isBadRequest());
        long id = crear(sesion, csrf, funcion);
        alta(sesion, csrf, funcion).andExpect(status().isConflict());
        mvc.perform(get(API).param("idFuncion", String.valueOf(funcion)).session(sesion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idFuncion").value(funcion));
        boleta(id, codigo + "1", "VENDIDA");
        boleta(id, codigo + "2", "USADA");
        boleta(id, codigo + "3", "ANULADA");
        editar(sesion, csrf, id, 1).andExpect(status().isConflict());
        editar(sesion, csrf, id, 1.5).andExpect(status().isBadRequest());
        editar(sesion, csrf, id, 2).andExpect(status().isOk()).andExpect(jsonPath("$.aforo").value(2));
        assertThat(jdbc.queryForObject("SELECT PRECIO FROM ZONA WHERE ID_ZONA = ?", BigDecimal.class, id))
                .isEqualByComparingTo("2000.25");
        assertThat(jdbc.queryForList("SELECT PRECIO_PAGADO FROM BOLETA WHERE ID_ZONA = ?", BigDecimal.class, id))
                .hasSize(3).allSatisfy(precio -> assertThat(precio).isEqualByComparingTo("1000"));
        jdbc.update("DELETE FROM BOLETA WHERE CODIGO IN (?, ?)", codigo + "1", codigo + "2");
        borrar(sesion, csrf, id).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BOLETA WHERE CODIGO = ?", Long.class, codigo + "3")).isEqualTo(1);
        jdbc.update("DELETE FROM BOLETA WHERE CODIGO = ?", codigo + "3");
        borrar(sesion, csrf, id).andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isNotFound());
    }

    @Test
    void encargadoSoloGestionaSuPrestadorYLasHistoricasSonDeSoloLectura() throws Exception {
        var prestadores = prestadores();
        long propia = funcion(prestadores.get(0), "PROGRAMADA");
        long ajena = funcion(prestadores.get(1), "PROGRAMADA");
        var sesion = login("ENCARGADO_PRESTADOR", prestadores.get(0));
        var csrf = token(sesion);
        long id = crear(sesion, csrf, propia);
        jdbc.update("INSERT INTO ZONA (ID_FUNCION,NOMBRE,AFORO,PRECIO) VALUES (?, 'Temporal', 3, 1000)", ajena);
        long idAjena = jdbc.queryForObject("SELECT ID_ZONA FROM ZONA WHERE ID_FUNCION = ?", Long.class, ajena);
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idZona == " + idAjena + ")]").isEmpty());
        mvc.perform(get(API).param("idFuncion", String.valueOf(ajena)).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/" + idAjena).session(sesion)).andExpect(status().isForbidden());
        alta(sesion, csrf, ajena).andExpect(status().isForbidden());
        editar(sesion, csrf, idAjena, 3).andExpect(status().isForbidden());
        borrar(sesion, csrf, idAjena).andExpect(status().isForbidden());
        editar(sesion, csrf, id, 3).andExpect(status().isOk());
        for (String estado : new String[]{"CANCELADA", "REALIZADA"}) {
            jdbc.update("UPDATE FUNCION SET ESTADO = ? WHERE ID_FUNCION = ?", estado, propia);
            mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk());
            alta(sesion, csrf, propia).andExpect(status().isConflict());
            editar(sesion, csrf, id, 3).andExpect(status().isConflict());
            borrar(sesion, csrf, id).andExpect(status().isConflict());
        }
    }

    private long funcion(long prestador, String estado) {
        long atraccion = jdbc.queryForObject("SELECT MIN(ID_ATRACCION) FROM ATRACCION WHERE ID_PRESTADOR = ?", Long.class, prestador);
        var clave = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(conexion -> {
            var insert = conexion.prepareStatement("""
                    INSERT INTO FUNCION (ID_ATRACCION, FECHA, HORA_APERTURA, HORA_CIERRE, ESTADO)
                    VALUES (?, DATE '2045-05-10', '09:00', '10:00', ?)
                    """, new String[]{"ID_FUNCION"});
            insert.setLong(1, atraccion);
            insert.setString(2, estado);
            return insert;
        }, clave);
        long id = clave.getKey().longValue();
        funciones.add(id);
        return id;
    }

    private void boleta(long zona, String identificador, String estado) {
        long reserva = jdbc.queryForObject("SELECT MIN(ID_RESERVA) FROM RESERVA", Long.class);
        long tipo = jdbc.queryForObject("SELECT MIN(ID_TIPO_ENTRADA) FROM TIPO_ENTRADA", Long.class);
        jdbc.update("""
                INSERT INTO BOLETA (CODIGO, ESTADO, PRECIO_PAGADO, ID_RESERVA, ID_ZONA, ID_TIPO_ENTRADA)
                VALUES (?, ?, 1000, ?, ?, ?)
                """, identificador, estado, reserva, zona, tipo);
    }

    private java.util.List<Long> prestadores() {
        return jdbc.queryForList("""
                SELECT DISTINCT ID_PRESTADOR FROM ATRACCION ORDER BY ID_PRESTADOR
                """, Long.class);
    }

    private long crear(MockHttpSession sesion, String csrf, long funcion) throws Exception {
        var respuesta = alta(sesion, csrf, funcion).andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idZona").asLong();
        assertThat(respuesta.getHeader("Location")).isEqualTo(API + "/" + id);
        return id;
    }

    private ResultActions alta(MockHttpSession sesion, String csrf, long funcion) throws Exception {
        return mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idFuncion", funcion, "nombre", "General", "aforo", 4, "precio", 1000))));
    }

    private ResultActions editar(MockHttpSession sesion, String csrf, long id, Number aforo) throws Exception {
        return mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nombre", "General", "aforo", aforo, "precio", new BigDecimal("2000.25")))));
    }

    private ResultActions borrar(MockHttpSession sesion, String csrf, long id) throws Exception {
        return mvc.perform(delete(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf));
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





