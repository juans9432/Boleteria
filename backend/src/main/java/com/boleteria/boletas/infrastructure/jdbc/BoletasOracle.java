package com.boleteria.boletas.infrastructure.jdbc;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;
import javax.sql.DataSource;
import com.boleteria.boletas.application.port.out.Boletas;
import com.boleteria.boletas.domain.Boleta;
import com.boleteria.boletas.domain.Boleta.*;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

public final class BoletasOracle implements Boletas {
    private static final String SELECT = """
        SELECT B.ID_BOLETA,B.CODIGO,B.ESTADO,B.PRECIO_PAGADO,B.ID_RESERVA,
               Z.ID_ZONA,Z.NOMBRE,T.ID_TIPO_ENTRADA,T.NOMBRE,B.LINEA_RESERVA,
               F.ID_FUNCION,A.NOMBRE,F.FECHA,F.HORA_APERTURA,F.HORA_CIERRE,F.ESTADO,B.ID_COMBO,D.GRUPO_COMBO
        FROM BOLETA B JOIN RESERVA R ON R.ID_RESERVA=B.ID_RESERVA
        JOIN PERFIL_CLIENTE P ON P.ID_PERFIL=R.ID_PERFIL
        JOIN ZONA Z ON Z.ID_ZONA=B.ID_ZONA
        JOIN FUNCION F ON F.ID_FUNCION=Z.ID_FUNCION
        JOIN ATRACCION A ON A.ID_ATRACCION=F.ID_ATRACCION
        JOIN TIPO_ENTRADA T ON T.ID_TIPO_ENTRADA=B.ID_TIPO_ENTRADA
        LEFT JOIN RESERVA_DETALLE D ON D.ID_RESERVA=B.ID_RESERVA AND D.LINEA=B.LINEA_RESERVA
        WHERE 1=1
        """;
    private final DataSource fuente;
    private final SQLErrorCodeSQLExceptionTranslator errores = new SQLErrorCodeSQLExceptionTranslator("Oracle");
    public BoletasOracle(DataSource fuente) { this.fuente = fuente; }
    public List<Boleta> listar(Alcance alcance) { return buscar(alcance, "", null); }
    public Optional<Boleta> consultar(Alcance alcance, long id) {
        return buscar(alcance, " AND B.ID_BOLETA=?", id).stream().findFirst();
    }
    public Optional<Boleta> porCodigo(Alcance alcance, String codigo) {
        return buscar(alcance, " AND B.CODIGO=?", codigo).stream().findFirst();
    }
    private List<Boleta> buscar(Alcance alcance, String filtro, Object parametro) {
        String regla = alcance.usuario() != null ? " AND P.ID_USUARIO=?"
                : alcance.prestador() != null ? " AND A.ID_PRESTADOR=?" : "";
        return ejecutar(c -> {
            try (var s = c.prepareStatement(SELECT + regla + filtro + " ORDER BY B.ID_BOLETA DESC")) {
                int i = 1;
                if (alcance.usuario() != null) s.setLong(i++, alcance.usuario());
                if (alcance.prestador() != null) s.setLong(i++, alcance.prestador());
                if (parametro != null) s.setObject(i, parametro);
                List<Boleta> resultado = new ArrayList<>();
                try (var r = s.executeQuery()) {
                    while (r.next()) {
                        int linea = r.getInt(10);
                        Integer vinculada = r.wasNull() ? null : linea;
                        resultado.add(new Boleta(r.getLong(1), r.getString(2), Estado.valueOf(r.getString(3)),
                                r.getBigDecimal(4), r.getLong(5), r.getLong(6), r.getString(7),
                                r.getLong(8), r.getString(9), vinculada, r.getLong(11), r.getString(12),
                                r.getDate(13).toLocalDate(), LocalTime.parse(r.getString(14)),
                                LocalTime.parse(r.getString(15)), r.getString(16), r.getObject(17, Long.class), r.getObject(18, Integer.class)));
                    }
                }
                return resultado;
            }
        });
    }
    public void bloquearUso(long funcion, long boleta) {
        long atraccion = bloquear("SELECT ID_ATRACCION FROM FUNCION WHERE ID_FUNCION=? FOR UPDATE WAIT 5", funcion);
        bloquear("SELECT ID_ATRACCION FROM ATRACCION WHERE ID_ATRACCION=? FOR UPDATE WAIT 5", atraccion);
        bloquear("SELECT ID_BOLETA FROM BOLETA WHERE ID_BOLETA=? FOR UPDATE WAIT 5", boleta);
    }
    private long bloquear(String sql, long id) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement(sql)) {
                s.setLong(1, id);
                try (var r = s.executeQuery()) {
                    if (!r.next()) throw new Ausente();
                    return r.getLong(1);
                }
            }
        });
    }
    public boolean marcarUsada(long id) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement("UPDATE BOLETA SET ESTADO='USADA' WHERE ID_BOLETA=? AND ESTADO='VENDIDA'")) {
                s.setLong(1, id);
                return s.executeUpdate() == 1;
            }
        });
    }
    private <T> T ejecutar(Operacion<T> op) {
        var c = DataSourceUtils.getConnection(fuente);
        try { return op.ejecutar(c); }
        catch (SQLException e) {
            if (Set.of(54, 60, 30006).contains(e.getErrorCode())) throw new Conflicto("La boleta se está procesando. Reintenta.");
            var error = errores.translate("Operación de boletas", null, e);
            if (error != null) throw error;
            throw new org.springframework.jdbc.UncategorizedSQLException("Operación de boletas", null, e);
        } finally { DataSourceUtils.releaseConnection(c, fuente); }
    }
    @FunctionalInterface private interface Operacion<T> { T ejecutar(Connection c) throws SQLException; }
}


