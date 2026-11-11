package com.boleteria.funciones;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
class FuncionesOracleTest {
    private static final String API = "/api/funciones";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String correo = UUID.randomUUID() + "@funciones.invalid";
    private final String marca = "Funcion prueba " + UUID.randomUUID();
    private final java.util.List<Long> atracciones = new ArrayList<>();
    private Long zona;

    @AfterEach
    void limpiar() {
        if (zona != null) jdbc.update("DELETE FROM ZONA WHERE ID_ZONA = ?", zona);
        for (Long id : atracciones) {
            jdbc.update("DELETE FROM FUNCION WHERE ID_ATRACCION = ?", id);
            jdbc.update("DELETE FROM ATRACCION WHERE ID_ATRACCION = ?", id);
        }
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO = ?", correo);
    }

    @Test
    void administraHorariosYConservaZonasYEstadosHistoricos() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        long atraccion = atraccion(prestadores().get(0));
        long id = crear(sesion, csrf, atraccion, "09:00", "10:00");
        long contigua = crear(sesion, csrf, atraccion, "10:00", "11:00");
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.horaApertura").value("09:00"));
        alta(sesion, csrf, datos(atraccion, "09:30", "10:30", "PROGRAMADA")).andExpect(status().isConflict());
        editar(sesion, csrf, contigua, datos(atraccion, "09:45", "11:00", "PROGRAMADA")).andExpect(status().isConflict());
        editar(sesion, csrf, id, datos(atraccion, "08:00", "10:00", "CANCELADA")).andExpect(status().isBadRequest());
        editar(sesion, csrf, id, datos(Long.MAX_VALUE, "08:00", "10:00", "PROGRAMADA")).andExpect(status().isBadRequest());
        editar(sesion, csrf, id, datos(atraccion, "08:00", "10:00", "PROGRAMADA")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT HORA_APERTURA FROM FUNCION WHERE ID_FUNCION = ?", String.class, id))
                .isEqualTo("08:00");

        jdbc.update("INSERT INTO ZONA (NOMBRE, AFORO, PRECIO, ID_FUNCION) VALUES (?, 10, 1000, ?)", marca.substring(0, 40), id);
        zona = jdbc.queryForObject("SELECT ID_ZONA FROM ZONA WHERE NOMBRE = ? AND ID_FUNCION = ?", Long.class, marca.substring(0, 40), id);
        borrar(sesion, csrf, id).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT ID_FUNCION FROM ZONA WHERE ID_ZONA = ?", Long.class, zona)).isEqualTo(id);
        jdbc.update("DELETE FROM ZONA WHERE ID_ZONA = ?", zona);
        zona = null;
        borrar(sesion, csrf, id).andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isNotFound());

        jdbc.update("UPDATE FUNCION SET ESTADO = 'REALIZADA' WHERE ID_FUNCION = ?", contigua);
        editar(sesion, csrf, contigua, datos(atraccion, "10:00", "12:00", "PROGRAMADA")).andExpect(status().isConflict());
        borrar(sesion, csrf, contigua).andExpect(status().isConflict());

        var conBoletas = jdbc.queryForList("""
                SELECT DISTINCT F.ID_FUNCION, F.ID_ATRACCION, F.FECHA, F.HORA_APERTURA, F.HORA_CIERRE
                FROM FUNCION F JOIN ZONA Z ON Z.ID_FUNCION = F.ID_FUNCION
                JOIN BOLETA B ON B.ID_ZONA = Z.ID_ZONA WHERE F.ESTADO = 'PROGRAMADA'
                """);
        assertThat(conBoletas).isNotEmpty();
        var existente = conBoletas.get(0);
        long idConBoletas = ((Number) existente.get("ID_FUNCION")).longValue();
        var carga = json.writeValueAsString(Map.of("idAtraccion", existente.get("ID_ATRACCION"),
                "fecha", existente.get("FECHA").toString().substring(0, 10), "horaApertura", existente.get("HORA_APERTURA"),
                "horaCierre", existente.get("HORA_CIERRE"), "estado", "PROGRAMADA"));
        editar(sesion, csrf, idConBoletas, carga).andExpect(status().isConflict());
        borrar(sesion, csrf, idConBoletas).andExpect(status().isConflict());
    }

    @Test
    void encargadoSoloGestionaFuncionesDeSuPrestador() throws Exception {
        var prestadores = prestadores();
        long propia = atraccion(prestadores.get(0));
        long ajena = atraccion(prestadores.get(1));
        jdbc.update("""
                INSERT INTO FUNCION (ID_ATRACCION, FECHA, HORA_APERTURA, HORA_CIERRE, ESTADO)
                VALUES (?, DATE '2035-05-10', '09:00', '10:00', 'PROGRAMADA')
                """, ajena);
        long idAjeno = jdbc.queryForObject("SELECT ID_FUNCION FROM FUNCION WHERE ID_ATRACCION = ?", Long.class, ajena);
        var sesion = login("ENCARGADO_PRESTADOR", prestadores.get(0));
        var csrf = token(sesion);
        long id = crear(sesion, csrf, propia, "09:00", "10:00");
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idFuncion == " + idAjeno + ")]").isEmpty());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + idAjeno).session(sesion)).andExpect(status().isForbidden());
        alta(sesion, csrf, datos(ajena, "11:00", "12:00", "PROGRAMADA")).andExpect(status().isForbidden());
        editar(sesion, csrf, id, datos(ajena, "11:00", "12:00", "PROGRAMADA")).andExpect(status().isForbidden());
        borrar(sesion, csrf, idAjeno).andExpect(status().isForbidden());
        editar(sesion, csrf, id, datos(propia, "11:00", "12:00", "PROGRAMADA")).andExpect(status().isOk());
        borrar(sesion, csrf, id).andExpect(status().isNoContent());
    }

    @Test
    void dosAltasConcurrentesNoPuedenGuardarElMismoHorario() throws Exception {
        long atraccion = atraccion(prestadores().get(0));
        var sesion = login("ADMINISTRADOR", null);
        var csrf = token(sesion);
        var inicio = new CountDownLatch(1);
        var ejecutor = Executors.newFixedThreadPool(2);
        try {
            var tarea = (java.util.concurrent.Callable<Integer>) () -> {
                inicio.await();
                return alta(sesion, csrf, datos(atraccion, "09:00", "10:00", "PROGRAMADA"))
                        .andReturn().getResponse().getStatus();
            };
            var primera = ejecutor.submit(tarea);
            var segunda = ejecutor.submit(tarea);
            inicio.countDown();
            var estados = new ArrayList<>(java.util.List.of(primera.get(12, TimeUnit.SECONDS), segunda.get(12, TimeUnit.SECONDS)));
            Collections.sort(estados);
            assertThat(estados).containsExactly(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FUNCION WHERE ID_ATRACCION = ?", Long.class, atraccion))
                    .isEqualTo(1);
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private long atraccion(long prestador) {
        String nombre = marca + "-" + atracciones.size();
        jdbc.update("INSERT INTO ATRACCION (NOMBRE, ID_PRESTADOR) VALUES (?, ?)", nombre, prestador);
        long id = jdbc.queryForObject("SELECT ID_ATRACCION FROM ATRACCION WHERE NOMBRE = ?", Long.class, nombre);
        atracciones.add(id);
        return id;
    }

    private java.util.List<Long> prestadores() {
        return jdbc.queryForList("SELECT ID_PRESTADOR FROM PRESTADOR ORDER BY ID_PRESTADOR", Long.class);
    }

    private String datos(long atraccion, String apertura, String cierre, String estado) throws Exception {
        return json.writeValueAsString(Map.of("idAtraccion", atraccion, "fecha", "2035-05-10",
                "horaApertura", apertura, "horaCierre", cierre, "estado", estado));
    }

    private long crear(MockHttpSession sesion, String csrf, long atraccion, String apertura, String cierre) throws Exception {
        var respuesta = alta(sesion, csrf, datos(atraccion, apertura, cierre, "PROGRAMADA"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idFuncion").asLong();
        assertThat(respuesta.getHeader("Location")).isEqualTo(API + "/" + id);
        return id;
    }

    private ResultActions alta(MockHttpSession sesion, String csrf, String datos) throws Exception {
        return mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos));
    }

    private ResultActions editar(MockHttpSession sesion, String csrf, long id, String datos) throws Exception {
        return mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos));
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




