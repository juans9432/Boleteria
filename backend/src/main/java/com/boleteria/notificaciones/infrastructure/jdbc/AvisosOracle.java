package com.boleteria.notificaciones.infrastructure.jdbc;

import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import com.boleteria.notificaciones.application.port.out.Avisos;
import com.boleteria.notificaciones.domain.Aviso;
import com.boleteria.notificaciones.domain.Aviso.Evento;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class AvisosOracle implements Avisos {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String SELECT = """
        SELECT ID_NOTIFICACION,ID_RESERVA,ASUNTO,MENSAJE,FECHA_HORA,LEIDA
          FROM NOTIFICACION WHERE ID_USUARIO=?
        """;
    private final DataSource fuente;
    private final SQLErrorCodeSQLExceptionTranslator errores = new SQLErrorCodeSQLExceptionTranslator("Oracle");
    public AvisosOracle(DataSource fuente) { this.fuente = fuente; }

    public List<Aviso> listar(long usuario) { return buscar(usuario, null); }
    public Optional<Aviso> consultar(long usuario, long id) {
        return buscar(usuario, id).stream().findFirst();
    }
    private List<Aviso> buscar(long usuario, Long id) {
        return ejecutar(c -> {
            String filtro = id == null ? "" : " AND ID_NOTIFICACION=?";
            try (var s = c.prepareStatement(SELECT + filtro + " ORDER BY FECHA_HORA DESC,ID_NOTIFICACION DESC")) {
                s.setLong(1, usuario);
                if (id != null) s.setLong(2, id);
                List<Aviso> resultado = new ArrayList<>();
                try (var r = s.executeQuery()) {
                    while (r.next()) {
                        long reserva = r.getLong(2);
                        Long vinculada = r.wasNull() ? null : reserva;
                        resultado.add(new Aviso(r.getLong(1), vinculada, r.getString(3), r.getString(4),
                                r.getObject(5, LocalDateTime.class).atZone(BOGOTA).toInstant(), "S".equals(r.getString(6))));
                    }
                }
                return resultado;
            }
        });
    }
    public boolean leer(long usuario, long id) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement("UPDATE NOTIFICACION SET LEIDA='S' WHERE ID_USUARIO=? AND ID_NOTIFICACION=?")) {
                s.setLong(1, usuario);
                s.setLong(2, id);
                return s.executeUpdate() == 1;
            }
        });
    }
    public void insertar(Evento evento, Instant fecha) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El aviso requiere la transacción de la operación.");
        }
        ejecutar(c -> {
            try (var s = c.prepareStatement("""
                INSERT /*+ IGNORE_ROW_ON_DUPKEY_INDEX(NOTIFICACION (CLAVE_EVENTO)) */
                INTO NOTIFICACION(ID_USUARIO,ID_RESERVA,ASUNTO,MENSAJE,FECHA_HORA,LEIDA,CLAVE_EVENTO)
                SELECT P.ID_USUARIO,R.ID_RESERVA,?,?,?,'N',? || ':' || TO_CHAR(P.ID_USUARIO)
                FROM RESERVA R JOIN PERFIL_CLIENTE P ON P.ID_PERFIL=R.ID_PERFIL
                WHERE R.ID_RESERVA=? AND P.ID_USUARIO IS NOT NULL
                """)) {
                s.setString(1, evento.asunto());
                s.setString(2, evento.mensaje());
                s.setObject(3, LocalDateTime.ofInstant(fecha, BOGOTA));
                s.setString(4, evento.operacion() + ":" + evento.reserva());
                s.setLong(5, evento.reserva());
                s.executeUpdate();
                return null;
            }
        });
    }
    private <T> T ejecutar(Operacion<T> op) {
        var c = DataSourceUtils.getConnection(fuente);
        try { return op.ejecutar(c); }
        catch (SQLException e) {
            var error = errores.translate("Notificaciones", null, e);
            if (error != null) throw error;
            throw new org.springframework.jdbc.UncategorizedSQLException("Notificaciones", null, e);
        } finally { DataSourceUtils.releaseConnection(c, fuente); }
    }
    @FunctionalInterface private interface Operacion<T> { T ejecutar(Connection c) throws SQLException; }
}

