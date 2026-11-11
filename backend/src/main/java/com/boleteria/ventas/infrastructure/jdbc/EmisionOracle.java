package com.boleteria.ventas.infrastructure.jdbc;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.boleteria.reservas.domain.Reserva.Conflicto;
import com.boleteria.ventas.application.port.out.Emision;
import com.boleteria.ventas.domain.Venta.*;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

public final class EmisionOracle implements Emision {
    private final DataSource fuente;
    private final SQLErrorCodeSQLExceptionTranslator errores;
    public EmisionOracle(DataSource fuente) {
        this.fuente = fuente;
        errores = new SQLErrorCodeSQLExceptionTranslator(fuente);
    }
    public void bloquear(Recurso recurso, List<Long> ids) {
        String tabla = switch (recurso) {
            case TIPO -> "TIPO_ENTRADA";
            default -> recurso.name();
        };
        for (long id : ids) {
            if (leer("SELECT ID_" + tabla + " FROM " + tabla + " WHERE ID_" + tabla + "=? FOR UPDATE WAIT 5",
                    r -> r.getLong(1), id).isEmpty()) throw new Conflicto("Cambió el catálogo de la selección.");
        }
    }
    public Optional<Tipo> tipo(long id) {
        return leer("SELECT PORCENTAJE_DESCUENTO,REQUIERE_ADULTO,NOMBRE FROM TIPO_ENTRADA WHERE ID_TIPO_ENTRADA=?",
                r -> new Tipo(r.getBigDecimal(1), "S".equals(r.getString(2)), "ADULTO".equalsIgnoreCase(r.getString(3).trim())), id)
                .stream().findFirst();
    }
    public List<Integer> numeros(long reserva) {
        return leer("SELECT LINEA FROM RESERVA_DETALLE WHERE ID_RESERVA=? ORDER BY LINEA", r -> r.getInt(1), reserva);
    }
    public int disponibles(long zona) {
        return leer("""
            SELECT Z.AFORO-(SELECT COUNT(*) FROM BOLETA B WHERE B.ID_ZONA=Z.ID_ZONA
                            AND B.ESTADO IN ('VENDIDA','USADA'))
            FROM ZONA Z WHERE Z.ID_ZONA=?
            """, r -> r.getInt(1), zona).get(0);
    }
    public int activas(long perfil, long funcion) {
        return leer("""
            SELECT COUNT(*) FROM BOLETA B JOIN RESERVA R ON R.ID_RESERVA=B.ID_RESERVA
            JOIN ZONA Z ON Z.ID_ZONA=B.ID_ZONA
            WHERE R.ID_PERFIL=? AND Z.ID_FUNCION=? AND B.ESTADO IN ('VENDIDA','USADA')
            """, r -> r.getInt(1), perfil, funcion).get(0);
    }
    public List<Boleta> emitidas(long reserva) {
        return leer("""
            SELECT CODIGO,LINEA_RESERVA,PRECIO_PAGADO FROM BOLETA WHERE ID_RESERVA=?
            AND LINEA_RESERVA IS NOT NULL ORDER BY LINEA_RESERVA
            """, r -> new Boleta(r.getString(1), r.getInt(2), r.getBigDecimal(3)), reserva);
    }
    public List<Boleta> emitir(long reserva, List<Linea> lineas) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement("""
                INSERT INTO BOLETA(CODIGO,ESTADO,PRECIO_PAGADO,ID_RESERVA,ID_ZONA,ID_TIPO_ENTRADA,ID_COMBO,LINEA_RESERVA)
                VALUES (?,'VENDIDA',?,?,?,?,?,?)
                """)) {
                List<Boleta> emitidas = new ArrayList<>();
                for (var linea : lineas) {
                    String codigo = UUID.randomUUID().toString();
                    s.setString(1, codigo);
                    s.setBigDecimal(2, linea.precio());
                    s.setLong(3, reserva);
                    s.setLong(4, linea.zona());
                    s.setLong(5, linea.tipo());
                    if (linea.combo() == null) s.setNull(6, Types.NUMERIC);
                    else s.setLong(6, linea.combo());
                    s.setInt(7, linea.numero());
                    s.addBatch();
                    emitidas.add(new Boleta(codigo, linea.numero(), linea.precio()));
                }
                s.executeBatch();
                return List.copyOf(emitidas);
            }
        });
    }
    private <T> List<T> leer(String sql, Fila<T> fila, long... parametros) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < parametros.length; i++) s.setLong(i + 1, parametros[i]);
                try (var r = s.executeQuery()) {
                    List<T> resultado = new ArrayList<>();
                    while (r.next()) resultado.add(fila.leer(r));
                    return resultado;
                }
            }
        });
    }
    private <T> T ejecutar(Operacion<T> operacion) {
        var c = DataSourceUtils.getConnection(fuente);
        try { return operacion.ejecutar(c); }
        catch (SQLException ex) {
            if (Set.of(1, 54, 60, 2291, 30006).contains(ex.getErrorCode())) {
                throw new Conflicto("La emisión entró en conflicto con otra operación.");
            }
            var error = errores.translate("Emisión de boletas", null, ex);
            if (error != null) throw error;
            throw new org.springframework.jdbc.UncategorizedSQLException("Emisión de boletas", null, ex);
        } finally { DataSourceUtils.releaseConnection(c, fuente); }
    }
    @FunctionalInterface private interface Fila<T> { T leer(ResultSet r) throws SQLException; }
    @FunctionalInterface private interface Operacion<T> { T ejecutar(Connection c) throws SQLException; }
}


