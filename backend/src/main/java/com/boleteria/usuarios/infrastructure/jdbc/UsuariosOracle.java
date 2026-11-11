package com.boleteria.usuarios.infrastructure.jdbc;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.usuarios.application.port.out.Usuarios;
import com.boleteria.usuarios.domain.Usuario;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class UsuariosOracle implements Usuarios {
    private static final String FORMATO_BCRYPT = "^[$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}$";
    private static final String UTILIZABLE = "REGEXP_LIKE(U.CLAVE_HASH, '" + FORMATO_BCRYPT + "', 'c')";
    private static final String SELECT = """
            SELECT U.ID_USUARIO, U.CORREO, U.ESTADO, U.ID_PRESTADOR, R.NOMBRE AS ROL,
            """ + "CASE WHEN " + UTILIZABLE + " THEN 1 ELSE 0 END AS UTILIZABLE "
            + "FROM BOLETERIA_FINAL.USUARIO_SISTEMA U JOIN BOLETERIA_FINAL.ROL R ON R.ID_ROL = U.ID_ROL ";
    private final DataSource dataSource;

    public UsuariosOracle(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<String> roles() {
        return ejecutar(conexion -> {
            var roles = new ArrayList<String>();
            try (var consulta = conexion.prepareStatement("""
                    SELECT NOMBRE FROM BOLETERIA_FINAL.ROL
                    WHERE NOMBRE IN ('ADMINISTRADOR', 'CLIENTE', 'ENCARGADO_PRESTADOR', 'OPERADOR')
                    ORDER BY NOMBRE
                    """); var filas = consulta.executeQuery()) {
                while (filas.next()) roles.add(filas.getString(1));
            }
            return roles;
        });
    }

    @Override
    public List<Usuario> listar() { return leer(SELECT + "ORDER BY U.CORREO, U.ID_USUARIO", null); }

    @Override
    public Optional<Usuario> consultar(long id) {
        return leer(SELECT + "WHERE U.ID_USUARIO = ?", id).stream().findFirst();
    }

    @Override
    public void bloquearCuenta(long id) {
        existe("SELECT ID_USUARIO FROM BOLETERIA_FINAL.USUARIO_SISTEMA WHERE ID_USUARIO = ? FOR UPDATE WAIT 5", id);
    }

    @Override
    public void bloquearAdministracion() {
        ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement("""
                    SELECT ID_ROL FROM BOLETERIA_FINAL.ROL WHERE NOMBRE = 'ADMINISTRADOR' FOR UPDATE WAIT 5
                    """); var filas = consulta.executeQuery()) {
                if (!filas.next()) throw new SQLException("Falta el rol administrador.");
                return null;
            }
        });
    }

    @Override
    public long administradoresUtilizables() {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement("""
                    SELECT COUNT(*) FROM BOLETERIA_FINAL.USUARIO_SISTEMA U
                    JOIN BOLETERIA_FINAL.ROL R ON R.ID_ROL = U.ID_ROL
                    WHERE R.NOMBRE = 'ADMINISTRADOR' AND U.ESTADO = 'ACTIVO' AND U.ID_PRESTADOR IS NULL AND
                    """ + UTILIZABLE); var filas = consulta.executeQuery()) {
                filas.next();
                return filas.getLong(1);
            }
        });
    }

    @Override
    public boolean prestadorExiste(long id) {
        return existe("SELECT 1 FROM BOLETERIA_FINAL.PRESTADOR WHERE ID_PRESTADOR = ?", id);
    }

    @Override
    public boolean tienePerfil(long id) {
        return existe("SELECT 1 FROM BOLETERIA_FINAL.PERFIL_CLIENTE WHERE ID_USUARIO = ?", id);
    }

    @Override
    public long insertar(Usuario.Datos datos, String hash) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.USUARIO_SISTEMA (CORREO, ESTADO, ID_ROL, ID_PRESTADOR, CLAVE_HASH)
                    VALUES (?, ?, (SELECT ID_ROL FROM BOLETERIA_FINAL.ROL WHERE NOMBRE = ?), ?, ?)
                    """, new String[]{"ID_USUARIO"})) {
                vincular(sentencia, datos, hash);
                sentencia.executeUpdate();
                try (var claves = sentencia.getGeneratedKeys()) {
                    if (!claves.next()) throw new SQLException("Oracle no devolvió el ID generado.");
                    return claves.getLong(1);
                }
            }
        });
    }

    @Override
    public void actualizar(long id, Usuario.Datos datos, String hash) {
        ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.USUARIO_SISTEMA
                    SET CORREO = ?, ESTADO = ?, ID_ROL = (SELECT ID_ROL FROM BOLETERIA_FINAL.ROL WHERE NOMBRE = ?),
                        ID_PRESTADOR = ?, CLAVE_HASH = COALESCE(?, CLAVE_HASH)
                    WHERE ID_USUARIO = ?
                    """)) {
                vincular(sentencia, datos, hash);
                sentencia.setLong(6, id);
                sentencia.executeUpdate();
                return null;
            }
        });
    }

    private static void vincular(PreparedStatement sentencia, Usuario.Datos datos, String hash) throws SQLException {
        sentencia.setString(1, datos.correo());
        sentencia.setString(2, datos.activo() ? "ACTIVO" : "INACTIVO");
        sentencia.setString(3, datos.rol().name());
        if (datos.idPrestador() == null) sentencia.setNull(4, Types.NUMERIC);
        else sentencia.setLong(4, datos.idPrestador());
        sentencia.setString(5, hash);
    }

    private List<Usuario> leer(String sql, Long id) {
        return ejecutar(conexion -> {
            var usuarios = new ArrayList<Usuario>();
            try (var consulta = conexion.prepareStatement(sql)) {
                if (id != null) consulta.setLong(1, id);
                try (var filas = consulta.executeQuery()) {
                    while (filas.next()) usuarios.add(new Usuario(filas.getLong("ID_USUARIO"), filas.getString("CORREO"),
                            Rol.valueOf(filas.getString("ROL")), "ACTIVO".equals(filas.getString("ESTADO")),
                            filas.getObject("ID_PRESTADOR", Long.class), filas.getInt("UTILIZABLE") == 1));
                }
            }
            return usuarios;
        });
    }

    private boolean existe(String sql, long id) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(sql)) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) { return fila.next(); }
            }
        });
    }

    private <T> T ejecutar(Operacion<T> operacion) {
        Connection conexion = DataSourceUtils.getConnection(dataSource);
        try {
            return operacion.ejecutar(conexion);
        } catch (SQLException error) {
            throw switch (error.getErrorCode()) {
                case 1 -> new Usuario.Conflicto("Ya existe una cuenta con ese correo.");
                case 2291 -> new Usuario.Invalido("El prestador seleccionado no existe.");
                case 12899 -> new Usuario.Invalido("El correo supera el espacio disponible en Oracle.");
                default -> new Fallo(error);
            };
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> { T ejecutar(Connection conexion) throws SQLException; }
}

