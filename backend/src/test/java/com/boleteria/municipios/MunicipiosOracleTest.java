package com.boleteria.municipios;

import java.util.ArrayList;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOLETERIA_DB_PASSWORD", matches = ".+")
class MunicipiosOracleTest {
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void apiCoincideConOracle() throws Exception {
        var esperado = new ArrayList<Map<String, Object>>();
        try (var conexion = dataSource.getConnection();
             var consulta = conexion.createStatement();
             var filas = consulta.executeQuery("""
                     SELECT ID_MUNICIPIO, NOMBRE, DEPARTAMENTO
                     FROM BOLETERIA_FINAL.MUNICIPIO
                     ORDER BY NOMBRE, ID_MUNICIPIO
                     """)) {
            while (filas.next()) {
                esperado.add(Map.of("idMunicipio", filas.getLong(1),
                        "nombre", filas.getString(2), "departamento", filas.getString(3)));
            }
        }

        mvc.perform(get("/api/municipios"))
                .andExpect(status().isOk())
                .andExpect(content().json(json.writeValueAsString(esperado),
                        org.springframework.test.json.JsonCompareMode.STRICT));
    }
}
