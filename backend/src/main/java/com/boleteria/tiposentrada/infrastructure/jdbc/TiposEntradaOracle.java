package com.boleteria.tiposentrada.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import com.boleteria.tiposentrada.application.port.out.TiposEntrada;
import com.boleteria.tiposentrada.domain.TipoEntrada;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class TiposEntradaOracle implements TiposEntrada {
    private static final String SELECT = """
            SELECT ID_TIPO_ENTRADA, NOMBRE, PORCENTAJE_DESCUENTO, REQUIERE_ADULTO FROM BOLETERIA_FINAL.TIPO_ENTRADA
            """;

    private final DataSource dataSource;

    public TiposEntradaOracle(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<TipoEntrada> listar() {
        return ejecutar(conexion -> {
            var tipos = new ArrayList<TipoEntrada>();
            try (var sentencia = conexion.prepareStatement(SELECT + "ORDER BY NOMBRE, ID_TIPO_ENTRADA");
                 var filas = sentencia.executeQuery()) {
                while (filas.next()) tipos.add(leer(filas));
            }
            return tipos;
        });
    }

    @Override
    public Optional<TipoEntrada> consultar(long id) {
        return consultar(id, false);
    }

    @Override public Optional<TipoEntrada> bloquear(long id) { return consultar(id, true); }

    private Optional<TipoEntrada> consultar(long id, boolean bloquear) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + "WHERE ID_TIPO_ENTRADA = ?"
                    + (bloquear ? " FOR UPDATE WAIT 5" : ""))) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public TipoEntrada crear(TipoEntrada.Datos datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.TIPO_ENTRADA (NOMBRE, PORCENTAJE_DESCUENTO, REQUIERE_ADULTO) VALUES (?, ?, ?)
                    """, new String[]{"ID_TIPO_ENTRADA"})) {
                vincular(sentencia, datos);
                sentencia.executeUpdate();
                try (var claves = sentencia.getGeneratedKeys()) {
                    if (!claves.next()) throw new SQLException("Oracle no devolvió el ID generado.");
                    return new TipoEntrada(claves.getLong(1), datos);
                }
            }
        });
    }

    @Override
    public boolean actualizar(TipoEntrada tipo) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.TIPO_ENTRADA SET NOMBRE = ?, PORCENTAJE_DESCUENTO = ?, REQUIERE_ADULTO = ? WHERE ID_TIPO_ENTRADA = ?
                    """)) {
                vincular(sentencia, tipo.datos());
                sentencia.setLong(4, tipo.id());
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    DELETE FROM BOLETERIA_FINAL.TIPO_ENTRADA WHERE ID_TIPO_ENTRADA = ?
                    """)) {
                sentencia.setLong(1, id);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    @Override public boolean tieneBoletas(long id) {
        return ejecutar(conexion -> {
            try (var s = conexion.prepareStatement("SELECT 1 FROM BOLETERIA_FINAL.BOLETA WHERE ID_TIPO_ENTRADA=? AND ROWNUM=1")) {
                s.setLong(1, id);
                try (var r = s.executeQuery()) { return r.next(); }
            }
        });
    }

    private static void vincular(java.sql.PreparedStatement sentencia, TipoEntrada.Datos datos) throws SQLException {
        sentencia.setString(1, datos.nombre());
        sentencia.setBigDecimal(2, datos.porcentajeDescuento());
        sentencia.setString(3, datos.requiereAdulto());
    }

    private static TipoEntrada leer(ResultSet fila) throws SQLException {
        return new TipoEntrada(fila.getLong("ID_TIPO_ENTRADA"), new TipoEntrada.Datos(fila.getString("NOMBRE"),
                fila.getBigDecimal("PORCENTAJE_DESCUENTO"), fila.getString("REQUIERE_ADULTO")));
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
                case 12899 -> new TipoEntrada.Invalido("El nombre supera el espacio disponible en Oracle.");
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


