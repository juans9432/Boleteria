package com.boleteria.atracciones.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.atracciones.application.port.out.Atracciones;
import com.boleteria.atracciones.application.port.out.Atracciones.Fallo.Motivo;
import com.boleteria.atracciones.domain.DatosAtraccion;
import com.boleteria.atracciones.domain.Atraccion;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class AtraccionesOracle implements Atracciones {
    private static final String SELECT = """
            SELECT ID_ATRACCION, NOMBRE, DESCRIPCION, ID_PRESTADOR
            FROM BOLETERIA_FINAL.ATRACCION
            """;
    private final DataSource dataSource;

    public AtraccionesOracle(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<Atraccion> listar(Long alcance) {
        return ejecutar(conexion -> {
            var atracciones = new ArrayList<Atraccion>();
            String filtro = alcance == null ? "" : "WHERE ID_PRESTADOR = ? ";
            try (var sentencia = conexion.prepareStatement(SELECT + filtro + "ORDER BY NOMBRE, ID_ATRACCION")) {
                if (alcance != null) sentencia.setLong(1, alcance);
                try (var filas = sentencia.executeQuery()) {
                    while (filas.next()) atracciones.add(leer(filas));
                }
            }
            return atracciones;
        });
    }

    @Override
    public Optional<Atraccion> consultar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + "WHERE ID_ATRACCION = ?")) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Atraccion crear(DatosAtraccion datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.ATRACCION (NOMBRE, DESCRIPCION, ID_PRESTADOR)
                    VALUES (?, ?, ?)
                    """, new String[]{"ID_ATRACCION"})) {
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
    public Optional<Atraccion> actualizar(long id, DatosAtraccion datos, Long alcance) {
        return ejecutar(conexion -> {
            String sql = """
                    UPDATE BOLETERIA_FINAL.ATRACCION SET NOMBRE = ?, DESCRIPCION = ?, ID_PRESTADOR = ?
                    WHERE ID_ATRACCION = ?
                    """ + (alcance == null ? "" : "AND ID_PRESTADOR = ?");
            try (var sentencia = conexion.prepareStatement(sql)) {
                vincular(sentencia, datos);
                sentencia.setLong(4, id);
                if (alcance != null) sentencia.setLong(5, alcance);
                return sentencia.executeUpdate() == 0 ? Optional.empty() : Optional.of(desde(id, datos));
            }
        });
    }

    @Override
    public boolean eliminar(long id, Long alcance) {
        return ejecutar(conexion -> {
            String sql = "DELETE FROM BOLETERIA_FINAL.ATRACCION WHERE ID_ATRACCION = ?"
                    + (alcance == null ? "" : " AND ID_PRESTADOR = ?");
            try (var sentencia = conexion.prepareStatement(sql)) {
                sentencia.setLong(1, id);
                if (alcance != null) sentencia.setLong(2, alcance);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    private static void vincular(PreparedStatement sentencia, DatosAtraccion datos) throws SQLException {
        sentencia.setString(1, datos.nombre());
        sentencia.setString(2, datos.descripcion());
        sentencia.setLong(3, datos.idPrestador());
    }

    private static Atraccion desde(long id, DatosAtraccion datos) {
        return new Atraccion(id, datos.nombre(), datos.descripcion(), datos.idPrestador());
    }

    private static Atraccion leer(ResultSet fila) throws SQLException {
        return new Atraccion(fila.getLong("ID_ATRACCION"), fila.getString("NOMBRE"), fila.getString("DESCRIPCION"),
                fila.getLong("ID_PRESTADOR"));
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
                case 2291 -> Motivo.PRESTADOR_INEXISTENTE;
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

