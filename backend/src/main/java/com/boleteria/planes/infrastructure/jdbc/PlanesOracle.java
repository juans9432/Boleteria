package com.boleteria.planes.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.planes.application.port.out.Planes;
import com.boleteria.planes.application.port.out.Planes.Fallo.Motivo;
import com.boleteria.planes.domain.DatosPlan;
import com.boleteria.planes.domain.Plan;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class PlanesOracle implements Planes {
    private static final String SELECT = """
            SELECT ID_PLAN, NOMBRE, DESCRIPCION, ESTADO, ID_PRESTADOR
            FROM BOLETERIA_FINAL.PLAN
            """;
    private final DataSource dataSource;

    public PlanesOracle(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<Plan> listar(Long alcance) {
        return ejecutar(conexion -> {
            var planes = new ArrayList<Plan>();
            String filtro = alcance == null ? "" : "WHERE ID_PRESTADOR = ? ";
            try (var sentencia = conexion.prepareStatement(SELECT + filtro + "ORDER BY NOMBRE, ID_PLAN")) {
                if (alcance != null) sentencia.setLong(1, alcance);
                try (var filas = sentencia.executeQuery()) {
                    while (filas.next()) planes.add(leer(filas));
                }
            }
            return planes;
        });
    }

    @Override
    public Optional<Plan> consultar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + "WHERE ID_PLAN = ?")) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Plan crear(DatosPlan datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.PLAN (NOMBRE, DESCRIPCION, ESTADO, ID_PRESTADOR)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"ID_PLAN"})) {
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
    public Optional<Plan> actualizar(long id, DatosPlan datos, Long alcance) {
        return ejecutar(conexion -> {
            String sql = """
                    UPDATE BOLETERIA_FINAL.PLAN SET NOMBRE = ?, DESCRIPCION = ?, ESTADO = ?, ID_PRESTADOR = ?
                    WHERE ID_PLAN = ?
                    """ + (alcance == null ? "" : "AND ID_PRESTADOR = ?");
            try (var sentencia = conexion.prepareStatement(sql)) {
                vincular(sentencia, datos);
                sentencia.setLong(5, id);
                if (alcance != null) sentencia.setLong(6, alcance);
                return sentencia.executeUpdate() == 0 ? Optional.empty() : Optional.of(desde(id, datos));
            }
        });
    }

    @Override
    public boolean eliminarSinReservas(long id, Long alcance) {
        return ejecutar(conexion -> {
            // El bloqueo SHARE ROW EXCLUSIVE impide cambios concurrentes en las referencias hasta finalizar la transacción.
            try (var bloqueo = conexion.prepareStatement("LOCK TABLE BOLETERIA_FINAL.RESERVA IN SHARE ROW EXCLUSIVE MODE WAIT 5")) {
                bloqueo.execute();
            }
            try (var consulta = conexion.prepareStatement(
                    "SELECT 1 FROM BOLETERIA_FINAL.RESERVA WHERE ID_PLAN = ? FETCH FIRST 1 ROW ONLY")) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    if (fila.next()) throw new Fallo(Motivo.REFERENCIADO, null);
                }
            }
            String sql = "DELETE FROM BOLETERIA_FINAL.PLAN WHERE ID_PLAN = ?"
                    + (alcance == null ? "" : " AND ID_PRESTADOR = ?");
            try (var sentencia = conexion.prepareStatement(sql)) {
                sentencia.setLong(1, id);
                if (alcance != null) sentencia.setLong(2, alcance);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    private static void vincular(PreparedStatement sentencia, DatosPlan datos) throws SQLException {
        sentencia.setString(1, datos.nombre());
        sentencia.setString(2, datos.descripcion());
        sentencia.setString(3, datos.estado());
        sentencia.setLong(4, datos.idPrestador());
    }

    private static Plan desde(long id, DatosPlan datos) {
        return new Plan(id, datos.nombre(), datos.descripcion(), datos.estado(), datos.idPrestador());
    }

    private static Plan leer(ResultSet fila) throws SQLException {
        return new Plan(fila.getLong("ID_PLAN"), fila.getString("NOMBRE"), fila.getString("DESCRIPCION"),
                fila.getString("ESTADO"), fila.getLong("ID_PRESTADOR"));
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

