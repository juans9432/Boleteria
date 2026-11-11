package com.boleteria.municipios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MunicipiosHttpTest {
    @MockitoBean DataSource dataSource;
    @Autowired MockMvc mvc;

    @Test
    void consultaPorJdbcYDevuelveLasFilas() throws Exception {
        var conexion = mock(Connection.class);
        var sentencia = mock(PreparedStatement.class);
        var filas = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conexion);
        when(conexion.prepareStatement(contains("BOLETERIA_FINAL.MUNICIPIO"))).thenReturn(sentencia);
        when(sentencia.executeQuery()).thenReturn(filas);
        when(filas.next()).thenReturn(true, false);
        when(filas.getLong("ID_MUNICIPIO")).thenReturn(42L);
        when(filas.getString("NOMBRE")).thenReturn("Armenia");
        when(filas.getString("DEPARTAMENTO")).thenReturn("Quindio");

        mvc.perform(get("/api/municipios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idMunicipio").value(42))
                .andExpect(jsonPath("$[0].nombre").value("Armenia"))
                .andExpect(jsonPath("$[0].departamento").value("Quindio"))
                .andExpect(jsonPath("$.length()").value(1));

        verify(filas).close();
        verify(sentencia).close();
        verify(conexion).close();
    }

    @Test
    void responde503SiOracleNoEstaDisponible() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Error de prueba"));
        mvc.perform(get("/api/municipios"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("No fue posible consultar los municipios."));
    }
}
