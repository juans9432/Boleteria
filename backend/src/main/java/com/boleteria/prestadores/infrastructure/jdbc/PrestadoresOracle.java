package com.boleteria.prestadores.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.prestadores.application.port.out.Prestadores;
import com.boleteria.prestadores.application.port.out.Prestadores.Fallo.Motivo;
import com.boleteria.prestadores.domain.DatosPrestador;
import com.boleteria.prestadores.domain.Prestador;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class PrestadoresOracle implements Prestadores {
    private static final String SELECT = """
            SELECT ID_PRESTADOR, NIT, NOMBRE, CORREO, ID_MUNICIPIO, ID_TIPO_PRESTADOR
            FROM BOLETERIA_FINAL.PRESTADOR
            """;
    private final DataSource dataSource;

    public PrestadoresOracle(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Prestador> listar() {
        return ejecutar(conexion -> {
            var prestadores = new ArrayList<Prestador>();
            try (var sentencia = conexion.prepareStatement(SELECT + "ORDER BY NOMBRE, ID_PRESTADOR");
                 var filas = sentencia.executeQuery()) {
                while (filas.next()) prestadores.add(leer(filas));
            }
            return prestadores;
        });
    }

    @Override
    public Optional<Prestador> consultar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + "WHERE ID_PRESTADOR = ?")) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Prestador crear(DatosPrestador datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.PRESTADOR (NIT, NOMBRE, CORREO, ID_MUNICIPIO, ID_TIPO_PRESTADOR)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"ID_PRESTADOR"})) {
                vincular(sentencia, datos);
                sentencia.executeUpdate();
                try (var claves = sentencia.getGeneratedKeys()) {
                    if (!claves.next()) throw new SQLException("Oracle no devolvió el ID generado.");
                    return desde(claves.getLong(1), datos);
                }
            }
        });
    }

    @Override
    public Optional<Prestador> actualizar(long id, DatosPrestador datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.PRESTADOR
                    SET NIT = ?, NOMBRE = ?, CORREO = ?, ID_MUNICIPIO = ?, ID_TIPO_PRESTADOR = ?
                    WHERE ID_PRESTADOR = ?
                    """)) {
                vincular(sentencia, datos);
                sentencia.setLong(6, id);
                return sentencia.executeUpdate() == 0 ? Optional.empty() : Optional.of(desde(id, datos));
            }
        });
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("DELETE FROM BOLETERIA_FINAL.PRESTADOR WHERE ID_PRESTADOR = ?")) {
                sentencia.setLong(1, id);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    private static void vincular(PreparedStatement sentencia, DatosPrestador datos) throws SQLException {
        sentencia.setString(1, datos.nit());
        sentencia.setString(2, datos.nombre());
        sentencia.setString(3, datos.correo());
        sentencia.setLong(4, datos.idMunicipio());
        sentencia.setLong(5, datos.idTipoPrestador());
    }

    private static Prestador desde(long id, DatosPrestador datos) {
        return new Prestador(id, datos.nit(), datos.nombre(), datos.correo(), datos.idMunicipio(), datos.idTipoPrestador());
    }

    private static Prestador leer(ResultSet fila) throws SQLException {
        return new Prestador(fila.getLong("ID_PRESTADOR"), fila.getString("NIT"), fila.getString("NOMBRE"),
                fila.getString("CORREO"), fila.getLong("ID_MUNICIPIO"), fila.getLong("ID_TIPO_PRESTADOR"));
    }

    private <T> T ejecutar(Operacion<T> operacion) {
        Connection conexion;
        try {
            conexion = DataSourceUtils.getConnection(dataSource);
        } catch (CannotGetJdbcConnectionException ex) {
            throw new Fallo(Motivo.CONEXION, ex);
        }
        try {
            return operacion.ejecutar(conexion);
        } catch (SQLException ex) {
            throw new Fallo(switch (ex.getErrorCode()) {
                case 1 -> Motivo.NIT_DUPLICADO;
                case 2291 -> Motivo.CATALOGO_INEXISTENTE;
                case 2292 -> Motivo.REFERENCIADO;
                case 12899 -> Motivo.CAMPO_EXTENSO;
                default -> Motivo.CONEXION;
            }, ex);
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }
}
