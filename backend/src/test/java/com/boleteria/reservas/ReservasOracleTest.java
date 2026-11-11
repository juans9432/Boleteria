package com.boleteria.reservas;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import com.boleteria.reservas.application.port.in.GestionarReservas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "boleteria.reservas.expiracion-activa=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "BOLETERIA_RESERVAS_QA", matches = "true")
class ReservasOracleTest {
    private static final String API = "/api/reservas";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired GestionarReservas casos;
    final String marca = UUID.randomUUID().toString().substring(0, 12);
    final String clave = UUID.randomUUID().toString();
    final List<Long> reservas = new ArrayList<>();
    final List<Long> zonas = new ArrayList<>();
    final List<Long> funciones = new ArrayList<>();

    @AfterEach void limpiar() {
        for (long id : reservas) {
            jdbc.update("DELETE FROM RESERVA_DETALLE WHERE ID_RESERVA=?", id);
            jdbc.update("DELETE FROM NOTIFICACION WHERE ID_RESERVA=?", id);
            jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA=?", id);
        }
        zonas.forEach(id -> jdbc.update("DELETE FROM ZONA WHERE ID_ZONA=?", id));
        funciones.forEach(id -> jdbc.update("DELETE FROM FUNCION WHERE ID_FUNCION=?", id));
        jdbc.update("DELETE FROM PERFIL_CLIENTE WHERE DOCUMENTO=?", marca);
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO LIKE ?", marca + "%@reservas.invalid");
    }

    @Test void persisteEditaCancelaYNoEmiteBoletas() throws Exception {
        var operador = login("op", "OPERADOR", null);
        long perfil = primero("SELECT MIN(ID_PERFIL) FROM PERFIL_CLIENTE");
        long atraccion = primero("SELECT MIN(ID_ATRACCION) FROM ATRACCION");
        long zona = oferta(atraccion);
        var solicitud = cuerpo(perfil, zona);
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/catalogos").session(operador)).andExpect(status().isOk());
        mvc.perform(post(API).session(operador).contentType("application/json").content(solicitud))
                .andExpect(status().isForbidden());
        var creada = alta(operador, solicitud);
        long id = creada.get("id").asLong();
        String vence = creada.get("vence").asText();
        mvc.perform(get(API).session(operador)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + id).session(operador)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lineas[0].precio").isNumber());
        mvc.perform(put(API + "/" + id).session(operador).header("X-CSRF-TOKEN", token(operador))
                .contentType("application/json").content(cuerpo(perfil, Long.MAX_VALUE))).andExpect(status().isBadRequest());
        assertThat(primero("SELECT ID_ZONA FROM RESERVA_DETALLE WHERE ID_RESERVA=" + id)).isEqualTo(zona);
        mvc.perform(put(API + "/" + id).session(operador).header("X-CSRF-TOKEN", token(operador))
                .contentType("application/json").content(solicitud)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vence").value(vence));
        assertThat(primero("SELECT COUNT(*) FROM BOLETA WHERE ID_RESERVA=" + id)).isZero();
        cancelar(operador, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        cancelar(operador, id).andExpect(status().isConflict());
        assertThat(primero("SELECT COUNT(*) FROM RESERVA_DETALLE WHERE ID_RESERVA=" + id)).isOne();
    }

    @Test void protegeClienteEncargadoHistorialYExpiraDesdeOracle() throws Exception {
        var admin = login("admin", "ADMINISTRADOR", null);
        var atracciones = jdbc.queryForList("SELECT MIN(ID_ATRACCION) FROM ATRACCION GROUP BY ID_PRESTADOR ORDER BY ID_PRESTADOR", Long.class);
        long prestador = primero("SELECT ID_PRESTADOR FROM ATRACCION WHERE ID_ATRACCION=" + atracciones.get(0));
        long propia = oferta(atracciones.get(0));
        long ajena = oferta(atracciones.get(1));
        var encargado = login("enc", "ENCARGADO_PRESTADOR", prestador);
        var cliente = login("cliente", "CLIENTE", null);
        long usuario = jdbc.queryForObject("SELECT ID_USUARIO FROM USUARIO_SISTEMA WHERE CORREO=?", Long.class, correo("cliente"));
        jdbc.update("INSERT INTO PERFIL_CLIENTE(DOCUMENTO,NOMBRE,ID_USUARIO) VALUES (?,'Temporal',?)", marca, usuario);
        long perfil = jdbc.queryForObject("SELECT ID_PERFIL FROM PERFIL_CLIENTE WHERE DOCUMENTO=?", Long.class, marca);
        long otro = primero("SELECT MIN(ID_PERFIL) FROM PERFIL_CLIENTE");
        long ajenaId = alta(admin, cuerpo(otro, ajena)).get("id").asLong();
        mvc.perform(get(API + "/" + ajenaId).session(encargado)).andExpect(status().isNotFound());
        mvc.perform(get(API + "/" + ajenaId).session(cliente)).andExpect(status().isNotFound());
        long propiaId = alta(cliente, cuerpo(perfil, propia)).get("id").asLong();
        mvc.perform(get(API + "/" + propiaId).session(encargado)).andExpect(status().isOk());
        jdbc.update("UPDATE RESERVA SET FECHA_HORA=TIMESTAMP '2000-01-01 00:00:00' WHERE ID_RESERVA=?", propiaId);
        mvc.perform(get(API + "/" + propiaId).session(cliente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADA"));
        cancelar(cliente, propiaId).andExpect(status().isConflict());
        casos.expirar();
        assertThat(primero("SELECT COUNT(*) FROM NOTIFICACION WHERE ID_RESERVA=" + propiaId)).isOne();
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM RESERVA WHERE ID_RESERVA=?", String.class, propiaId))
                .isEqualTo("EXPIRADA");
        long historica = primero("SELECT MIN(ID_RESERVA) FROM RESERVA WHERE ESTADO='CONFIRMADA'");
        cancelar(admin, historica).andExpect(status().isConflict());
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transacciones;
    @Autowired com.boleteria.reservas.infrastructure.jdbc.ReservasOracle adaptador;

    @Test void revierteCabeceraSiElDetalleFallaYSerializaCancelaciones() throws Exception {
        long perfil = primero("SELECT MIN(ID_PERFIL) FROM PERFIL_CLIENTE");
        long tipo = primero("SELECT MIN(ID_TIPO_ENTRADA) FROM TIPO_ENTRADA");
        var idFallido = new java.util.concurrent.atomic.AtomicLong();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transacciones);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long id = adaptador.insertar(perfil, null, java.time.Instant.now());
            idFallido.set(id);
            var seleccion = new com.boleteria.reservas.domain.Reserva.Seleccion(Long.MAX_VALUE, tipo, null, null);
            adaptador.reemplazarLineas(id, List.of(new com.boleteria.reservas.domain.Reserva.Linea(
                    seleccion, java.math.BigDecimal.ONE, false, null, null)));
        })).isInstanceOf(com.boleteria.reservas.domain.Reserva.Conflicto.class);
        assertThat(idFallido.get()).isPositive();
        assertThat(primero("SELECT COUNT(*) FROM RESERVA WHERE ID_RESERVA=" + idFallido.get())).isZero();

        var operador = login("op", "OPERADOR", null);
        long id = alta(operador, cuerpo(perfil, oferta(primero("SELECT MIN(ID_ATRACCION) FROM ATRACCION")))).get("id").asLong();
        var actor = new com.boleteria.reservas.domain.Reserva.Actor(1, com.boleteria.identidad.domain.Rol.OPERADOR, null);
        var inicio = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> cancelar = () -> {
            inicio.await();
            try { casos.cancelar(actor, id); return true; }
            catch (com.boleteria.reservas.domain.Reserva.Conflicto esperado) { return false; }
        };
        var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var una = ejecutor.submit(cancelar);
            var otra = ejecutor.submit(cancelar);
            inicio.countDown();
            assertThat(List.of(una.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    otra.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { ejecutor.shutdownNow(); }
    }

    private long oferta(long atraccion) {
        jdbc.update("INSERT INTO FUNCION(ID_ATRACCION,FECHA,HORA_APERTURA,HORA_CIERRE,ESTADO) VALUES (?, DATE '2099-01-01','10:00','11:00','PROGRAMADA')", atraccion);
        long funcion = primero("SELECT MAX(ID_FUNCION) FROM FUNCION");
        funciones.add(funcion);
        jdbc.update("INSERT INTO ZONA(ID_FUNCION,NOMBRE,AFORO,PRECIO) VALUES (?,?,1,12345)", funcion, marca);
        long zona = primero("SELECT MAX(ID_ZONA) FROM ZONA");
        zonas.add(zona);
        return zona;
    }

    private String cuerpo(long perfil, long zona) {
        return "{\"perfil\":" + perfil + ",\"selecciones\":[{\"zona\":" + zona + ",\"tipo\":"
                + primero("SELECT MIN(ID_TIPO_ENTRADA) FROM TIPO_ENTRADA") + "}]}";
    }
    private com.fasterxml.jackson.databind.JsonNode alta(MockHttpSession s, String cuerpo) throws Exception {
        var resultado = json.readTree(mvc.perform(post(API).session(s).header("X-CSRF-TOKEN", token(s))
                .contentType("application/json").content(cuerpo)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        reservas.add(resultado.get("id").asLong());
        return resultado;
    }
    private ResultActions cancelar(MockHttpSession s, long id) throws Exception {
        return mvc.perform(post(API + "/" + id + "/cancelar").session(s).header("X-CSRF-TOKEN", token(s)));
    }
    private MockHttpSession login(String sufijo, String rol, Long prestador) throws Exception {
        jdbc.update("""
            INSERT INTO USUARIO_SISTEMA(CORREO,CLAVE_HASH,ESTADO,ID_ROL,ID_PRESTADOR)
            SELECT ?,?,'ACTIVO',ID_ROL,? FROM ROL WHERE NOMBRE=?
            """, correo(sufijo), encoder.encode(clave), new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NUMERIC, prestador), rol);
        var s = new MockHttpSession();
        return (MockHttpSession) mvc.perform(post("/api/auth/login").session(s).header("X-CSRF-TOKEN", token(s))
                .param("correo", correo(sufijo)).param("clave", clave)).andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }
    private String token(MockHttpSession s) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(s)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    private String correo(String sufijo) { return marca + sufijo + "@reservas.invalid"; }
    private long primero(String sql) { return jdbc.queryForObject(sql, Long.class); }
}






