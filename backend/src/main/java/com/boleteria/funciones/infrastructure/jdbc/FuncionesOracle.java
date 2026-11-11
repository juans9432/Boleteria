package com.boleteria.funciones.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.funciones.application.port.out.Funciones;
import com.boleteria.funciones.domain.DatosFuncion;
import com.boleteria.funciones.domain.Funcion;
import com.boleteria.funciones.domain.Funcion.Estado;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class FuncionesOracle implements Funciones {
    private static final String SELECT = """
            SELECT F.ID_FUNCION, F.ID_ATRACCION, F.FECHA, F.HORA_APERTURA, F.HORA_CIERRE, F.ESTADO
            FROM BOLETERIA_FINAL.FUNCION F
            """;
    private final DataSource dataSource;

    public FuncionesOracle(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<Funcion> listar(Long prestador) {
        String filtro = prestador == null ? "" : """
                JOIN BOLETERIA_FINAL.ATRACCION A ON A.ID_ATRACCION = F.ID_ATRACCION
                WHERE A.ID_PRESTADOR = ?
                """;
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + filtro + "ORDER BY F.FECHA, F.HORA_APERTURA, F.ID_FUNCION")) {
                if (prestador != null) sentencia.setLong(1, prestador);
                return leerLista(sentencia);
            }
        });
    }

    @Override
    public Optional<Funcion> consultar(long id, boolean bloquear) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + "WHERE F.ID_FUNCION = ?" + bloqueo(bloquear))) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Long> prestadorDeAtraccion(long id, boolean bloquear) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    SELECT ID_PRESTADOR FROM BOLETERIA_FINAL.ATRACCION WHERE ID_ATRACCION = ?
                    """ + bloqueo(bloquear))) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(fila.getLong(1)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Funcion> agenda(long id, LocalDate fecha) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(SELECT + """
                    WHERE F.ID_ATRACCION = ? AND F.FECHA >= ? AND F.FECHA < ?
                    """)) {
                sentencia.setLong(1, id);
                sentencia.setDate(2, Date.valueOf(fecha));
                sentencia.setDate(3, Date.valueOf(fecha.plusDays(1)));
                return leerLista(sentencia);
            }
        });
    }

    @Override
    public Funcion crear(DatosFuncion datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.FUNCION
                    (ID_ATRACCION, FECHA, HORA_APERTURA, HORA_CIERRE, ESTADO)
                    VALUES (?, ?, ?, ?, 'PROGRAMADA')
                    """, new String[]{"ID_FUNCION"})) {
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
    public Funcion actualizar(long id, DatosFuncion datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.FUNCION
                    SET ID_ATRACCION = ?, FECHA = ?, HORA_APERTURA = ?, HORA_CIERRE = ?
                    WHERE ID_FUNCION = ?
                    """)) {
                vincular(sentencia, datos);
                sentencia.setLong(5, id);
                if (sentencia.executeUpdate() != 1) throw new SQLException("No se actualizó la función bloqueada.");
                return desde(id, datos);
            }
        });
    }

    @Override
    public void bloquearEscriturasDeZonas() {
        ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(
                    "LOCK TABLE BOLETERIA_FINAL.ZONA IN SHARE ROW EXCLUSIVE MODE WAIT 5")) {
                sentencia.execute();
                return null;
            }
        });
    }

    @Override
    public boolean tieneZonas(long id) {
        return existe("SELECT 1 FROM BOLETERIA_FINAL.ZONA WHERE ID_FUNCION = ? FETCH FIRST 1 ROW ONLY", id);
    }

    @Override
    public boolean tieneBoletas(long id) {
        return existe("""
                SELECT 1 FROM BOLETERIA_FINAL.BOLETA B
                JOIN BOLETERIA_FINAL.ZONA Z ON Z.ID_ZONA = B.ID_ZONA
                WHERE Z.ID_FUNCION = ? FETCH FIRST 1 ROW ONLY
                """, id);
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("DELETE FROM BOLETERIA_FINAL.FUNCION WHERE ID_FUNCION = ?")) {
                sentencia.setLong(1, id);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    private boolean existe(String sql, long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement(sql)) {
                sentencia.setLong(1, id);
                try (var fila = sentencia.executeQuery()) { return fila.next(); }
            }
        });
    }

    private static String bloqueo(boolean bloquear) { return bloquear ? " FOR UPDATE WAIT 5" : ""; }

    private static void vincular(PreparedStatement sentencia, DatosFuncion datos) throws SQLException {
        sentencia.setLong(1, datos.idAtraccion());
        sentencia.setDate(2, Date.valueOf(datos.fecha()));
        sentencia.setString(3, datos.apertura().toString());
        sentencia.setString(4, datos.cierre().toString());
    }

    private static Funcion desde(long id, DatosFuncion d) {
        return new Funcion(id, d.idAtraccion(), d.fecha(), d.apertura(), d.cierre(), Estado.PROGRAMADA);
    }

    private static List<Funcion> leerLista(PreparedStatement sentencia) throws SQLException {
        var datos = new ArrayList<Funcion>();
        try (var filas = sentencia.executeQuery()) {
            while (filas.next()) datos.add(leer(filas));
        }
        return datos;
    }

    private static Funcion leer(ResultSet fila) throws SQLException {
        return new Funcion(fila.getLong("ID_FUNCION"), fila.getLong("ID_ATRACCION"),
                fila.getDate("FECHA").toLocalDate(), LocalTime.parse(fila.getString("HORA_APERTURA")),
                LocalTime.parse(fila.getString("HORA_CIERRE")), Estado.valueOf(fila.getString("ESTADO")));
    }

    private <T> T ejecutar(Operacion<T> operacion) {
        Connection conexion;
        try {
            conexion = DataSourceUtils.getConnection(dataSource);
        } catch (CannotGetJdbcConnectionException error) {
            throw new Fallo(error);
        }
        try {
            return operacion.ejecutar(conexion);
        } catch (SQLException error) {
            throw new Fallo(error);
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> { T ejecutar(Connection conexion) throws SQLException; }
}
