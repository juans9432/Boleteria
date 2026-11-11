package com.boleteria.cancelaciones.infrastructure.jdbc;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.cancelaciones.application.port.out.Cancelaciones;
import com.boleteria.cancelaciones.domain.Cancelacion.*;
import com.boleteria.ventas.infrastructure.jdbc.EmisionOracle;
import com.boleteria.ventas.application.port.out.Emision.Recurso;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

public final class CancelacionesOracle implements Cancelaciones {
    private static final String ENTRADAS = """
        SELECT B.ID_BOLETA,B.ID_RESERVA,F.ID_FUNCION,A.ID_PRESTADOR,B.ID_COMBO,D.GRUPO_COMBO,
               B.ESTADO,T.REQUIERE_ADULTO,T.NOMBRE,B.CODIGO
        FROM BOLETA B JOIN ZONA Z ON Z.ID_ZONA=B.ID_ZONA JOIN FUNCION F ON F.ID_FUNCION=Z.ID_FUNCION
        JOIN ATRACCION A ON A.ID_ATRACCION=F.ID_ATRACCION
        JOIN TIPO_ENTRADA T ON T.ID_TIPO_ENTRADA=B.ID_TIPO_ENTRADA
        LEFT JOIN RESERVA_DETALLE D ON D.ID_RESERVA=B.ID_RESERVA AND D.LINEA=B.LINEA_RESERVA
        """;
    private final DataSource fuente;
    private final EmisionOracle bloqueos;
    private final SQLErrorCodeSQLExceptionTranslator errores = new SQLErrorCodeSQLExceptionTranslator("Oracle");
    public CancelacionesOracle(DataSource fuente) {
        this.fuente = fuente;
        this.bloqueos = new EmisionOracle(fuente);
    }
    public Optional<Entrada> entrada(long id) { return entradas("B.ID_BOLETA", id).stream().findFirst(); }
    public List<Entrada> deReserva(long id) { return entradas("B.ID_RESERVA", id); }
    public List<Entrada> deFuncion(long id) { return entradas("F.ID_FUNCION", id); }
    private List<Entrada> entradas(String columna, long id) {
        return leer(ENTRADAS + " WHERE " + columna + "=? ORDER BY B.ID_BOLETA", id, r -> {
            long combo = r.getLong(5);
            Long comboId = r.wasNull() ? null : combo;
            int grupo = r.getInt(6);
            Integer grupoId = r.wasNull() ? null : grupo;
            return new Entrada(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), comboId, grupoId,
                    Estado.valueOf(r.getString(7)), "S".equals(r.getString(8)),
                    "ADULTO".equalsIgnoreCase(r.getString(9).trim()), r.getString(10));
        });
    }
    public Optional<Funcion> funcion(long id) {
        return leer("""
            SELECT F.ID_ATRACCION,A.ID_PRESTADOR,F.ESTADO FROM FUNCION F
            JOIN ATRACCION A ON A.ID_ATRACCION=F.ID_ATRACCION WHERE F.ID_FUNCION=?
            """, id, r -> new Funcion(r.getLong(1), r.getLong(2), r.getString(3))).stream().findFirst();
    }
    public void bloquearFunciones(List<Long> ids) { bloquear(Recurso.FUNCION, ids); }
    public void bloquearAtracciones(List<Long> ids) { bloquear(Recurso.ATRACCION, ids); }
    private void bloquear(Recurso recurso, List<Long> ids) {
        try { bloqueos.bloquear(recurso, ids); }
        catch (com.boleteria.reservas.domain.Reserva.Conflicto error) { throw new Conflicto(error.getMessage()); }
    }
    public void bloquearPlan(long id) { bloquear(Recurso.PLAN, List.of(id)); }
    public void bloquearBoletas(List<Long> ids) {
        for (long id : ids) {
            if (leer("SELECT ID_BOLETA FROM BOLETA WHERE ID_BOLETA=? FOR UPDATE WAIT 5", id, r -> r.getLong(1)).isEmpty()) {
                throw new Ausente();
            }
        }
    }
    public void anular(List<Long> ids) {
        for (long id : ids) actualizar("UPDATE BOLETA SET ESTADO='ANULADA' WHERE ID_BOLETA=? AND ESTADO='VENDIDA'", id);
    }
    public void cancelarReserva(long id) {
        actualizar("UPDATE RESERVA SET ESTADO='CANCELADA' WHERE ID_RESERVA=? AND ESTADO='CONFIRMADA'", id);
    }
    public void cancelarFuncion(long id) {
        actualizar("UPDATE FUNCION SET ESTADO='CANCELADA' WHERE ID_FUNCION=? AND ESTADO='PROGRAMADA'", id);
    }
    private void actualizar(String sql, long id) {
        ejecutar(c -> {
            try (var s = c.prepareStatement(sql)) {
                s.setLong(1, id);
                if (s.executeUpdate() != 1) throw new Conflicto("El estado cambió durante la operación.");
                return null;
            }
        });
    }
    private <T> List<T> leer(String sql, long id, Fila<T> fila) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement(sql)) {
                s.setLong(1, id);
                try (var r = s.executeQuery()) {
                    var resultado = new ArrayList<T>();
                    while (r.next()) resultado.add(fila.leer(r));
                    return resultado;
                }
            }
        });
    }
    private <T> T ejecutar(Operacion<T> op) {
        var c = DataSourceUtils.getConnection(fuente);
        try { return op.ejecutar(c); }
        catch (SQLException e) {
            if (Set.of(54,60,30006).contains(e.getErrorCode())) throw new Conflicto("Otra operación bloqueó el registro. Reintenta.");
            var error = errores.translate("Cancelación", null, e);
            if (error != null) throw error;
            throw new org.springframework.jdbc.UncategorizedSQLException("Cancelación", null, e);
        } finally { DataSourceUtils.releaseConnection(c, fuente); }
    }
    @FunctionalInterface private interface Operacion<T> { T ejecutar(Connection c) throws SQLException; }
    @FunctionalInterface private interface Fila<T> { T leer(ResultSet r) throws SQLException; }
}



