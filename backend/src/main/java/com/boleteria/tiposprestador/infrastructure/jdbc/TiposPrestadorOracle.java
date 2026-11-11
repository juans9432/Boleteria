package com.boleteria.tiposprestador.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import com.boleteria.tiposprestador.application.port.out.TiposPrestador;
import com.boleteria.tiposprestador.domain.TipoPrestador;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class TiposPrestadorOracle implements TiposPrestador {
    private static final String SELECT = """
            SELECT ID_TIPO_PRESTADOR, NOMBRE FROM BOLETERIA_FINAL.TIPO_PRESTADOR
            """;

    private final DataSource dataSource;

    public TiposPrestadorOracle(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<TipoPrestador> listar() {
        return ejecutar(conexion -> {
            var tipos = new ArrayList<TipoPrestador>();
            try (var sentencia = conexion.prepareStatement(SELECT + "ORDER BY NOMBRE, ID_TIPO_PRESTADOR");
                 var filas = sentencia.executeQuery()) {
                while (filas.next()) tipos.add(leer(filas));
            }
            return tipos;
        });
    }

    @Override
    public Optional<TipoPrestador> consultar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + "WHERE ID_TIPO_PRESTADOR = ?")) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public TipoPrestador crear(String nombre) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.TIPO_PRESTADOR (NOMBRE) VALUES (?)
                    """, new String[]{"ID_TIPO_PRESTADOR"})) {
                sentencia.setString(1, nombre);
                sentencia.executeUpdate();
                try (var claves = sentencia.getGeneratedKeys()) {
                    if (!claves.next()) throw new SQLException("Oracle no devolvió el ID generado.");
                    return new TipoPrestador(claves.getLong(1), nombre);
                }
            }
        });
    }

    @Override
    public boolean actualizar(TipoPrestador tipo) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.TIPO_PRESTADOR SET NOMBRE = ? WHERE ID_TIPO_PRESTADOR = ?
                    """)) {
                sentencia.setString(1, tipo.nombre());
                sentencia.setLong(2, tipo.id());
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    DELETE FROM BOLETERIA_FINAL.TIPO_PRESTADOR WHERE ID_TIPO_PRESTADOR = ?
                    """)) {
                sentencia.setLong(1, id);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    private static TipoPrestador leer(ResultSet fila) throws SQLException {
        return new TipoPrestador(fila.getLong("ID_TIPO_PRESTADOR"), fila.getString("NOMBRE"));
    }

    private <T> T ejecutar(Operacion<T> operacion) {
        Connection conexion;
        try {
            conexion = DataSourceUtils.getConnection(dataSource);
        } catch (org.springframework.jdbc.CannotGetJdbcConnectionException ex) {
            throw new PersistenciaFallida(ex);
        }
        try {
            return operacion.ejecutar(conexion);
        } catch (SQLException ex) {
            throw switch (ex.getErrorCode()) {
                case 1 -> new NombreDuplicado(ex);
                case 2292 -> new TipoReferenciado(ex);
                case 12899 -> new TipoPrestador.Invalido("El nombre supera el espacio disponible en Oracle.");
                default -> new PersistenciaFallida(ex);
            };
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }
}
