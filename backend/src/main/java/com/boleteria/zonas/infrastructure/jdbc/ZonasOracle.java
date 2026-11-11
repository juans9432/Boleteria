package com.boleteria.zonas.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.zonas.application.port.out.Zonas;
import com.boleteria.zonas.domain.Zona;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class ZonasOracle implements Zonas {
    private static final String SELECT = """
            SELECT Z.ID_ZONA, Z.ID_FUNCION, Z.NOMBRE, Z.AFORO, Z.PRECIO
            FROM BOLETERIA_FINAL.ZONA Z
            """;
    private final DataSource dataSource;

    public ZonasOracle(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<Zona> listar(Long prestador, Long idFuncion) {
        String alcance = prestador == null ? "" : """
                JOIN BOLETERIA_FINAL.FUNCION F ON F.ID_FUNCION = Z.ID_FUNCION
                JOIN BOLETERIA_FINAL.ATRACCION A ON A.ID_ATRACCION = F.ID_ATRACCION
                """;
        var condiciones = new ArrayList<String>();
        if (prestador != null) condiciones.add("A.ID_PRESTADOR = ?");
        if (idFuncion != null) condiciones.add("Z.ID_FUNCION = ?");
        String filtro = condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones) + " ";
        return ejecutar(conexion -> {
            var zonas = new ArrayList<Zona>();
            try (var consulta = conexion.prepareStatement(SELECT + alcance + filtro + "ORDER BY Z.ID_FUNCION, Z.NOMBRE, Z.ID_ZONA")) {
                if (prestador != null) consulta.setLong(1, prestador);
                if (idFuncion != null) consulta.setLong(prestador == null ? 1 : 2, idFuncion);
                try (var filas = consulta.executeQuery()) {
                    while (filas.next()) zonas.add(leer(filas));
                }
            }
            return zonas;
        });
    }

    @Override
    public Optional<Zona> consultar(long id, boolean bloquear) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(SELECT + "WHERE Z.ID_ZONA = ?" + bloqueo(bloquear))) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Funcion> funcion(long id, boolean bloquear) {
        return ejecutar(conexion -> {
            long atraccion;
            String estado;
            try (var consulta = conexion.prepareStatement("""
                    SELECT ID_ATRACCION, ESTADO FROM BOLETERIA_FINAL.FUNCION WHERE ID_FUNCION = ?
                    """ + bloqueo(bloquear))) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    if (!fila.next()) return Optional.empty();
                    atraccion = fila.getLong("ID_ATRACCION");
                    estado = fila.getString("ESTADO");
                }
            }
            try (var consulta = conexion.prepareStatement("""
                    SELECT ID_PRESTADOR FROM BOLETERIA_FINAL.ATRACCION WHERE ID_ATRACCION = ?
                    """ + bloqueo(bloquear))) {
                consulta.setLong(1, atraccion);
                try (var fila = consulta.executeQuery()) {
                    return fila.next() ? Optional.of(new Funcion(fila.getLong(1), estado)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Zona crear(long idFuncion, Zona.Datos datos) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.ZONA (NOMBRE, AFORO, PRECIO, ID_FUNCION) VALUES (?, ?, ?, ?)
                    """, new String[]{"ID_ZONA"})) {
                vincular(consulta, datos);
                consulta.setLong(4, idFuncion);
                consulta.executeUpdate();
                try (var claves = consulta.getGeneratedKeys()) {
                    if (!claves.next()) throw new SQLException("Oracle no devolvió el ID generado.");
                    return desde(claves.getLong(1), idFuncion, datos);
                }
            }
        });
    }

    @Override
    public Zona actualizar(Zona actual, Zona.Datos datos) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.ZONA SET NOMBRE = ?, AFORO = ?, PRECIO = ? WHERE ID_ZONA = ?
                    """)) {
                vincular(consulta, datos);
                consulta.setLong(4, actual.id());
                if (consulta.executeUpdate() != 1) throw new SQLException("No se actualizó la zona bloqueada.");
                return desde(actual.id(), actual.idFuncion(), datos);
            }
        });
    }

    @Override
    public long ocupacion(long id) {
        return contar("SELECT COUNT(*) FROM BOLETERIA_FINAL.BOLETA WHERE ID_ZONA = ? AND ESTADO IN ('VENDIDA','USADA')", id);
    }

    @Override
    public boolean tieneBoletas(long id) {
        return contar("SELECT COUNT(*) FROM BOLETERIA_FINAL.BOLETA WHERE ID_ZONA = ?", id) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement("DELETE FROM BOLETERIA_FINAL.ZONA WHERE ID_ZONA = ?")) {
                consulta.setLong(1, id);
                return consulta.executeUpdate() > 0;
            }
        });
    }

    private long contar(String sql, long id) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(sql)) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    if (!fila.next()) throw new SQLException("Oracle no devolvió el conteo.");
                    return fila.getLong(1);
                }
            }
        });
    }

    private static String bloqueo(boolean bloquear) { return bloquear ? " FOR UPDATE WAIT 5" : ""; }

    private static void vincular(PreparedStatement consulta, Zona.Datos datos) throws SQLException {
        consulta.setString(1, datos.nombre());
        consulta.setInt(2, datos.aforo());
        consulta.setBigDecimal(3, datos.precio());
    }

    private static Zona desde(long id, long idFuncion, Zona.Datos datos) {
        return new Zona(id, idFuncion, datos.nombre(), datos.aforo(), datos.precio());
    }

    private static Zona leer(ResultSet fila) throws SQLException {
        return new Zona(fila.getLong("ID_ZONA"), fila.getLong("ID_FUNCION"), fila.getString("NOMBRE"),
                fila.getInt("AFORO"), fila.getBigDecimal("PRECIO"));
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
            throw switch (error.getErrorCode()) {
                case 1 -> new Zona.Conflicto("Ya existe una zona con ese nombre en la función.");
                case 2292 -> new Zona.Conflicto("La zona tiene boletas asociadas y no se puede eliminar.");
                case 2291 -> new Zona.Invalida("La función seleccionada no existe.");
                case 12899 -> new Zona.Invalida("El nombre supera el espacio disponible en Oracle.");
                default -> new Fallo(error);
            };
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> { T ejecutar(Connection conexion) throws SQLException; }
}
