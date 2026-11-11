package com.boleteria.municipios.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import com.boleteria.municipios.application.port.out.ConsultarMunicipios;
import com.boleteria.municipios.application.port.out.PersistirMunicipios;
import com.boleteria.municipios.domain.DatosMunicipio;
import com.boleteria.municipios.domain.Municipio;

public class MunicipiosOracle implements ConsultarMunicipios, PersistirMunicipios {
    private static final String SELECT = """
            SELECT ID_MUNICIPIO, NOMBRE, DEPARTAMENTO
            FROM BOLETERIA_FINAL.MUNICIPIO
            """;

    private final DataSource dataSource;

    public MunicipiosOracle(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Municipio> listar() {
        List<Municipio> municipios = new ArrayList<>();
        try (var conexion = dataSource.getConnection();
             var consulta = conexion.prepareStatement(SELECT + "ORDER BY NOMBRE, ID_MUNICIPIO");
             var filas = consulta.executeQuery()) {
            while (filas.next()) {
                municipios.add(leer(filas));
            }
            return municipios;
        } catch (SQLException ex) {
            throw new ConsultaFallida(ex);
        }
    }

    @Override
    public Optional<Municipio> consultar(long id) {
        try (var conexion = dataSource.getConnection();
             var consulta = conexion.prepareStatement(SELECT + "WHERE ID_MUNICIPIO = ?")) {
            consulta.setLong(1, id);
            try (var fila = consulta.executeQuery()) {
                return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new ConsultaFallida(ex);
        }
    }

    @Override
    public Municipio crear(DatosMunicipio datos) {
        try (var conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                long id;
                try (var insert = conexion.prepareStatement("""
                        INSERT INTO BOLETERIA_FINAL.MUNICIPIO (NOMBRE, DEPARTAMENTO) VALUES (?, ?)
                        """, new String[]{"ID_MUNICIPIO"})) {
                    insert.setString(1, datos.nombre());
                    insert.setString(2, datos.departamento());
                    insert.executeUpdate();
                    try (var claves = insert.getGeneratedKeys()) {
                        if (!claves.next()) {
                            throw new SQLException("Oracle no devolvió el ID generado.");
                        }
                        id = claves.getLong(1);
                    }
                }
                conexion.commit();
                return new Municipio(id, datos.nombre(), datos.departamento());
            } catch (SQLException ex) {
                try {
                    conexion.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        } catch (SQLException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public Optional<Municipio> actualizar(long id, DatosMunicipio datos) {
        try (var conexion = dataSource.getConnection();
             var update = conexion.prepareStatement("""
                     UPDATE BOLETERIA_FINAL.MUNICIPIO SET NOMBRE = ?, DEPARTAMENTO = ?
                     WHERE ID_MUNICIPIO = ?
                     """)) {
            update.setString(1, datos.nombre());
            update.setString(2, datos.departamento());
            update.setLong(3, id);
            return update.executeUpdate() == 0 ? Optional.empty()
                    : Optional.of(new Municipio(id, datos.nombre(), datos.departamento()));
        } catch (SQLException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public boolean eliminar(long id) {
        try (var conexion = dataSource.getConnection();
             var delete = conexion.prepareStatement(
                     "DELETE FROM BOLETERIA_FINAL.MUNICIPIO WHERE ID_MUNICIPIO = ?")) {
            delete.setLong(1, id);
            return delete.executeUpdate() != 0;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 2292) {
                throw new MunicipioReferenciado(ex);
            }
            throw new ConsultaFallida(ex);
        }
    }

    private static Municipio leer(ResultSet fila) throws SQLException {
        return new Municipio(fila.getLong("ID_MUNICIPIO"), fila.getString("NOMBRE"),
                fila.getString("DEPARTAMENTO"));
    }

    private static RuntimeException traducir(SQLException ex) {
        if (ex.getErrorCode() == 12899) {
            return new DatosMunicipio.Invalido("El nombre o departamento supera el espacio disponible en Oracle.");
        }
        return new ConsultaFallida(ex);
    }
}
