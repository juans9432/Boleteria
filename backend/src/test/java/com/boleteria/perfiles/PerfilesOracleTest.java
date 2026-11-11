package com.boleteria.perfiles;

import java.util.HashMap;
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
class PerfilesOracleTest {
    private static final String API = "/api/perfiles";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final String clave = UUID.randomUUID().toString();
    private final String marca = UUID.randomUUID().toString().substring(0, 16);
    private Long reserva;

    @AfterEach
    void limpiar() {
        if (reserva != null) jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA = ?", reserva);
        jdbc.update("DELETE FROM PERFIL_CLIENTE WHERE DOCUMENTO IN (?, ?, ?)", marca, marca + "B", marca + "C");
        jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE CORREO LIKE ?", marca + "%@perfiles.invalid");
    }

    @Test
    void administraMostradorVinculacionUnicidadYReferencias() throws Exception {
        long admin = cuenta("admin", "ADMINISTRADOR", "ACTIVO", null);
        long cliente = cuenta("cliente", "CLIENTE", "ACTIVO", null);
        long inactivo = cuenta("inactivo", "CLIENTE", "INACTIVO", null);
        var sesion = login("admin");
        var csrf = token(sesion);
        mvc.perform(get(API + "/cuentas-vinculables").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUsuario == " + cliente + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.idUsuario == " + inactivo + ")]").isEmpty());
        long id = crear(sesion, csrf, marca, null);
        assertThat(jdbc.queryForObject("SELECT ID_USUARIO FROM PERFIL_CLIENTE WHERE ID_PERFIL = ?", Long.class, id)).isNull();
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isOk()).andExpect(jsonPath("$.documento").value(marca));
        alta(sesion, csrf, marca, null).andExpect(status().isConflict());
        alta(sesion, csrf, marca + "B", Long.MAX_VALUE).andExpect(status().isBadRequest());
        alta(sesion, csrf, marca + "B", admin).andExpect(status().isBadRequest());
        alta(sesion, csrf, marca + "B", inactivo).andExpect(status().isBadRequest());
        editar(sesion, csrf, id, marca, cliente).andExpect(status().isOk()).andExpect(jsonPath("$.idUsuario").value(cliente));
        alta(sesion, csrf, marca + "B", cliente).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La cuenta ya está vinculada a otro perfil."));
        mvc.perform(get(API + "/cuentas-vinculables").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUsuario == " + cliente + ")]").isEmpty());

        var claveReserva = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(conexion -> {
            var insert = conexion.prepareStatement("""
                    INSERT INTO RESERVA (ESTADO, ID_PERFIL) VALUES ('CONFIRMADA', ?)
                    """, new String[]{"ID_RESERVA"});
            insert.setLong(1, id);
            return insert;
        }, claveReserva);
        reserva = claveReserva.getKey().longValue();
        borrar(sesion, csrf, id).andExpect(status().isConflict());
        editar(sesion, csrf, id, marca, null).andExpect(status().isConflict());
        editar(sesion, csrf, id, marca + "C", cliente).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT ID_PERFIL FROM RESERVA WHERE ID_RESERVA = ?", Long.class, reserva))
                .isEqualTo(id);
        jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA = ?", reserva);
        reserva = null;
        borrar(sesion, csrf, id).andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id).session(sesion)).andExpect(status().isNotFound());
    }

    @Test
    void aplicaAlcancePorRolYClienteSoloAccedeASuPerfil() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        long cliente = cuenta("cliente", "CLIENTE", "ACTIVO", null);
        cuenta("operador", "OPERADOR", "ACTIVO", null);
        long prestador = jdbc.queryForObject("SELECT MIN(ID_PRESTADOR) FROM PRESTADOR", Long.class);
        cuenta("encargado", "ENCARGADO_PRESTADOR", "ACTIVO", prestador);
        cuenta("admin", "ADMINISTRADOR", "ACTIVO", null);
        var admin = login("admin");
        long propio = crear(admin, token(admin), marca, cliente);
        var operador = login("operador");
        var tokenOperador = token(operador);
        long mostrador = crear(operador, tokenOperador, marca + "B", null);
        editar(operador, tokenOperador, mostrador, marca + "B", null).andExpect(status().isOk());
        alta(operador, tokenOperador, marca + "C", cliente).andExpect(status().isForbidden());
        mvc.perform(get(API + "/" + propio).session(operador)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/cuentas-vinculables").session(operador)).andExpect(status().isForbidden());
        borrar(operador, tokenOperador, mostrador).andExpect(status().isForbidden());

        var sesion = login("cliente");
        var csrf = token(sesion);
        mvc.perform(get(API).session(sesion)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(API + "/" + propio).session(sesion)).andExpect(status().isOk());
        mvc.perform(get(API + "/" + mostrador).session(sesion)).andExpect(status().isForbidden());
        editar(sesion, csrf, mostrador, marca + "B", cliente).andExpect(status().isForbidden());
        editar(sesion, csrf, propio, marca + "C", cliente).andExpect(status().isForbidden());
        editar(sesion, csrf, propio, marca, null).andExpect(status().isForbidden());
        editar(sesion, csrf, propio, marca, cliente).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT TELEFONO FROM PERFIL_CLIENTE WHERE ID_PERFIL = ?", String.class, propio))
                .isEqualTo("+57 310 1234567");
        mvc.perform(get(API).session(login("encargado"))).andExpect(status().isForbidden());
    }

    private long cuenta(String sufijo, String rol, String estado, Long prestador) {
        jdbc.update("""
                INSERT INTO USUARIO_SISTEMA (CORREO, CLAVE_HASH, ESTADO, ID_ROL, ID_PRESTADOR)
                SELECT ?, ?, ?, ID_ROL, ? FROM ROL WHERE NOMBRE = ?
                """, correo(sufijo), encoder.encode(clave), estado,
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NUMERIC, prestador), rol);
        return jdbc.queryForObject("SELECT ID_USUARIO FROM USUARIO_SISTEMA WHERE CORREO = ?", Long.class, correo(sufijo));
    }

    private String correo(String sufijo) { return marca + sufijo + "@perfiles.invalid"; }

    private String datos(String documento, Long usuario) throws Exception {
        Map<String, Object> datos = new HashMap<>();
        datos.put("documento", documento);
        datos.put("nombre", "Perfil de prueba");
        datos.put("telefono", "+57 310 1234567");
        datos.put("idUsuario", usuario);
        return json.writeValueAsString(datos);
    }

    private long crear(MockHttpSession sesion, String csrf, String documento, Long usuario) throws Exception {
        var respuesta = alta(sesion, csrf, documento, usuario).andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("idPerfil").asLong();
        assertThat(respuesta.getHeader("Location")).isEqualTo(API + "/" + id);
        return id;
    }

    private ResultActions alta(MockHttpSession sesion, String csrf, String documento, Long usuario) throws Exception {
        return mvc.perform(post(API).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos(documento, usuario)));
    }

    private ResultActions editar(MockHttpSession sesion, String csrf, long id, String documento, Long usuario) throws Exception {
        return mvc.perform(put(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(datos(documento, usuario)));
    }

    private ResultActions borrar(MockHttpSession sesion, String csrf, long id) throws Exception {
        return mvc.perform(delete(API + "/" + id).session(sesion).header("X-CSRF-TOKEN", csrf));
    }

    private MockHttpSession login(String sufijo) throws Exception {
        var sesion = new MockHttpSession();
        return (MockHttpSession) mvc.perform(post("/api/auth/login").session(sesion)
                .header("X-CSRF-TOKEN", token(sesion)).param("correo", correo(sufijo)).param("clave", clave))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private String token(MockHttpSession sesion) throws Exception {
        var respuesta = mvc.perform(get("/api/auth/csrf").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
