package com.boleteria.identidad.infrastructure.jdbc;

import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.identidad.application.port.out.Cuentas;
import com.boleteria.identidad.domain.Cuenta;
import com.boleteria.identidad.domain.Rol;
import org.springframework.dao.DataAccessResourceFailureException;

public class CuentasOracle implements Cuentas {
    private final DataSource dataSource;

    public CuentasOracle(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Credencial> buscarPorCorreo(String correo) {
        try (var conexion = dataSource.getConnection();
             var sentencia = conexion.prepareStatement("""
                     SELECT U.ID_USUARIO, U.CORREO, U.CLAVE_HASH, U.ESTADO,
                            R.NOMBRE AS ROL, U.ID_PRESTADOR
                     FROM USUARIO_SISTEMA U JOIN ROL R ON R.ID_ROL = U.ID_ROL
                     WHERE U.CORREO = ?
                     """)) {
            sentencia.setString(1, correo);
            try (var fila = sentencia.executeQuery()) {
                if (!fila.next()) {
                    return Optional.empty();
                }
                Rol rol = switch (fila.getString("ROL")) {
                    case "ADMINISTRADOR" -> Rol.ADMINISTRADOR;
                    case "OPERADOR" -> Rol.OPERADOR;
                    case "CLIENTE" -> Rol.CLIENTE;
                    case "ENCARGADO_PRESTADOR" -> Rol.ENCARGADO_PRESTADOR;
                    default -> null;
                };
                var cuenta = new Cuenta(fila.getLong("ID_USUARIO"), fila.getString("CORREO"), rol,
                        "ACTIVO".equals(fila.getString("ESTADO")), fila.getObject("ID_PRESTADOR", Long.class));
                return Optional.of(new Credencial(cuenta, fila.getString("CLAVE_HASH")));
            }
        } catch (SQLException ex) {
            throw new DataAccessResourceFailureException("No fue posible consultar identidad", ex);
        }
    }

    @Override
    public boolean configurarClaveInicial(String correo, String hash) {
        try (var conexion = dataSource.getConnection();
             var sentencia = conexion.prepareStatement("""
                     UPDATE USUARIO_SISTEMA SET CLAVE_HASH = ?
                     WHERE CORREO = ? AND CLAVE_HASH = 'PENDIENTE_CONFIGURACION'
                     """)) {
            sentencia.setString(1, hash);
            sentencia.setString(2, correo);
            return sentencia.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DataAccessResourceFailureException("No fue posible configurar la clave", ex);
        }
    }
}
