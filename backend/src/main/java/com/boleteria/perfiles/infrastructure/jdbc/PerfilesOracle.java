package com.boleteria.perfiles.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.perfiles.application.port.in.GestionarPerfiles.CuentaVinculable;
import com.boleteria.perfiles.application.port.out.Perfiles;
import com.boleteria.perfiles.domain.Perfil;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class PerfilesOracle implements Perfiles {
    private static final String SELECT = """
            SELECT P.ID_PERFIL, P.DOCUMENTO, P.NOMBRE, P.TELEFONO, P.ID_USUARIO, U.CORREO
            FROM BOLETERIA_FINAL.PERFIL_CLIENTE P
            LEFT JOIN BOLETERIA_FINAL.USUARIO_SISTEMA U ON U.ID_USUARIO = P.ID_USUARIO
            """;
    private final DataSource dataSource;

    public PerfilesOracle(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<Perfil> listarTodos() { return leerLista("", null); }

    @Override
    public List<Perfil> listarMostrador() { return leerLista("WHERE P.ID_USUARIO IS NULL ", null); }

    @Override
    public Optional<Perfil> buscarPorUsuario(long id) {
        return leerLista("WHERE P.ID_USUARIO = ? ", id).stream().findFirst();
    }

    private List<Perfil> leerLista(String filtro, Long id) {
        return ejecutar(conexion -> {
            var perfiles = new ArrayList<Perfil>();
            try (var consulta = conexion.prepareStatement(SELECT + filtro + "ORDER BY P.NOMBRE, P.ID_PERFIL")) {
                if (id != null) consulta.setLong(1, id);
                try (var filas = consulta.executeQuery()) {
                    while (filas.next()) perfiles.add(leer(filas));
                }
            }
            return perfiles;
        });
    }

    @Override
    public Optional<Perfil> consultar(long id, boolean bloquear) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(SELECT + "WHERE P.ID_PERFIL = ?"
                    + (bloquear ? " FOR UPDATE OF P.ID_PERFIL WAIT 5" : ""))) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    return fila.next() ? Optional.of(leer(fila)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Cuenta> bloquearCuenta(long id) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement("""
                    SELECT U.ID_USUARIO, U.CORREO, R.NOMBRE AS ROL, U.ESTADO
                    FROM BOLETERIA_FINAL.USUARIO_SISTEMA U
                    JOIN BOLETERIA_FINAL.ROL R ON R.ID_ROL = U.ID_ROL
                    WHERE U.ID_USUARIO = ? FOR UPDATE OF U.ID_USUARIO WAIT 5
                    """)) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    return fila.next() ? Optional.of(new Cuenta(fila.getLong(1), fila.getString(2),
                            fila.getString(3), fila.getString(4))) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<CuentaVinculable> cuentasVinculables() {
        return ejecutar(conexion -> {
            var cuentas = new ArrayList<CuentaVinculable>();
            try (var consulta = conexion.prepareStatement("""
                    SELECT U.ID_USUARIO, U.CORREO
                    FROM BOLETERIA_FINAL.USUARIO_SISTEMA U
                    JOIN BOLETERIA_FINAL.ROL R ON R.ID_ROL = U.ID_ROL
                    WHERE R.NOMBRE = 'CLIENTE' AND U.ESTADO = 'ACTIVO'
                      AND NOT EXISTS (SELECT 1 FROM BOLETERIA_FINAL.PERFIL_CLIENTE P WHERE P.ID_USUARIO = U.ID_USUARIO)
                    ORDER BY U.CORREO
                    """); var filas = consulta.executeQuery()) {
                while (filas.next()) cuentas.add(new CuentaVinculable(filas.getLong(1), filas.getString(2)));
            }
            return cuentas;
        });
    }

    @Override
    public long insertar(Perfil.Datos datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.PERFIL_CLIENTE (DOCUMENTO, NOMBRE, TELEFONO, ID_USUARIO)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"ID_PERFIL"})) {
                vincular(sentencia, datos);
                sentencia.executeUpdate();
                try (var claves = sentencia.getGeneratedKeys()) {
                    if (!claves.next()) throw new SQLException("Oracle no devolvió el ID generado.");
                    return claves.getLong(1);
                }
            }
        });
    }

    @Override
    public void actualizar(long id, Perfil.Datos datos) {
        ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.PERFIL_CLIENTE SET DOCUMENTO = ?, NOMBRE = ?, TELEFONO = ?, ID_USUARIO = ?
                    WHERE ID_PERFIL = ?
                    """)) {
                vincular(sentencia, datos);
                sentencia.setLong(5, id);
                sentencia.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void bloquearEscriturasDeReservas() {
        ejecutar(conexion -> {
            try (var bloqueo = conexion.prepareStatement("LOCK TABLE BOLETERIA_FINAL.RESERVA IN SHARE ROW EXCLUSIVE MODE WAIT 5")) {
                bloqueo.execute();
                return null;
            }
        });
    }

    @Override
    public boolean tieneReservas(long id) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(
                    "SELECT 1 FROM BOLETERIA_FINAL.RESERVA WHERE ID_PERFIL = ? FETCH FIRST 1 ROW ONLY")) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) { return fila.next(); }
            }
        });
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("DELETE FROM BOLETERIA_FINAL.PERFIL_CLIENTE WHERE ID_PERFIL = ?")) {
                sentencia.setLong(1, id);
                return sentencia.executeUpdate() > 0;
            }
        });
    }

    private static void vincular(PreparedStatement sentencia, Perfil.Datos datos) throws SQLException {
        sentencia.setString(1, datos.documento());
        sentencia.setString(2, datos.nombre());
        sentencia.setString(3, datos.telefono());
        if (datos.idUsuario() == null) sentencia.setNull(4, java.sql.Types.NUMERIC);
        else sentencia.setLong(4, datos.idUsuario());
    }

    private static Perfil leer(ResultSet fila) throws SQLException {
        return new Perfil(fila.getLong("ID_PERFIL"), new Perfil.Datos(fila.getString("DOCUMENTO"),
                fila.getString("NOMBRE"), fila.getString("TELEFONO"), fila.getObject("ID_USUARIO", Long.class)),
                fila.getString("CORREO"));
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
                case 1 -> new Perfil.Conflicto(error.getMessage().contains("UK_PERFIL_USUARIO")
                        ? "La cuenta ya está vinculada a otro perfil." : "Ya existe un perfil con ese documento.");
                case 2291 -> new Perfil.Invalido("La cuenta seleccionada no existe.");
                case 2292 -> new Perfil.Conflicto("El perfil tiene reservas asociadas y no se puede eliminar.");
                case 12899 -> new Perfil.Invalido("Un campo supera el espacio disponible en Oracle.");
                default -> new Fallo(error);
            };
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> { T ejecutar(Connection conexion) throws SQLException; }
}

