package com.boleteria.reservas.infrastructure.jdbc;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import javax.sql.DataSource;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.reservas.application.port.out.Reservas;
import com.boleteria.reservas.domain.Reserva;
import com.boleteria.reservas.domain.Reserva.*;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

public class ReservasOracle implements Reservas {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String LINEAS = """
        SELECT ID_ZONA, ID_TIPO_ENTRADA, ID_COMBO, GRUPO_COMBO, PRECIO_ORIENTATIVO PRECIO, 0 EMITIDA, LINEA ORDEN, NULL CODIGO, NULL ESTADO_BOLETA
          FROM RESERVA_DETALLE D WHERE ID_RESERVA=?
          AND NOT EXISTS (SELECT 1 FROM BOLETA B WHERE B.ID_RESERVA=D.ID_RESERVA)
        UNION ALL
        SELECT B.ID_ZONA, B.ID_TIPO_ENTRADA, B.ID_COMBO, D.GRUPO_COMBO, B.PRECIO_PAGADO, 1, B.ID_BOLETA, B.CODIGO, B.ESTADO
          FROM BOLETA B LEFT JOIN RESERVA_DETALLE D ON D.ID_RESERVA=B.ID_RESERVA AND D.LINEA=B.LINEA_RESERVA
          WHERE B.ID_RESERVA=?
        ORDER BY EMITIDA, ORDEN
        """;
    private final DataSource source;
    private final SQLErrorCodeSQLExceptionTranslator errores;
    public ReservasOracle(DataSource source) {
        this.source = source;
        this.errores = new SQLErrorCodeSQLExceptionTranslator(source);
    }

    public Catalogos catalogos(Actor actor) {
        boolean cliente = actor.rol() == Rol.CLIENTE;
        boolean encargado = actor.rol() == Rol.ENCARGADO_PRESTADOR;
        Object[] alcance = encargado ? new Object[]{actor.prestador()} : new Object[0];
        return new Catalogos(
                opciones("SELECT ID_PERFIL,NOMBRE FROM PERFIL_CLIENTE"
                        + (cliente ? " WHERE ID_USUARIO=?" : "") + " ORDER BY NOMBRE",
                        cliente ? new Object[]{actor.usuario()} : new Object[0]),
                opciones("SELECT ID_PLAN,NOMBRE FROM PLAN WHERE ESTADO='ACTIVO'"
                        + (encargado ? " AND ID_PRESTADOR=?" : "") + " ORDER BY NOMBRE", alcance),
                opciones("""
                    SELECT Z.ID_ZONA,A.NOMBRE||' · '||TO_CHAR(F.FECHA,'YYYY-MM-DD')||' '||
                           F.HORA_APERTURA||' · '||Z.NOMBRE
                    FROM ZONA Z JOIN FUNCION F ON F.ID_FUNCION=Z.ID_FUNCION
                    JOIN ATRACCION A ON A.ID_ATRACCION=F.ID_ATRACCION WHERE F.ESTADO='PROGRAMADA'
                    """ + (encargado ? " AND A.ID_PRESTADOR=?" : "") + " ORDER BY F.FECHA,F.HORA_APERTURA,Z.NOMBRE", alcance),
                opciones("SELECT ID_TIPO_ENTRADA,NOMBRE FROM TIPO_ENTRADA ORDER BY NOMBRE"),
                opciones("""
                    SELECT C.ID_COMBO,C.NOMBRE FROM COMBO C WHERE C.ESTADO='ACTIVO'
                    """ + (encargado ? """
                      AND NOT EXISTS (SELECT 1 FROM COMBO_ATRACCION CA
                        JOIN ATRACCION A ON A.ID_ATRACCION=CA.ID_ATRACCION
                        WHERE CA.ID_COMBO=C.ID_COMBO AND A.ID_PRESTADOR<>?)
                    """ : "") + " ORDER BY C.NOMBRE", alcance));
    }
    private List<Opcion> opciones(String sql, Object... args) {
        return query(sql, args, r -> new Opcion(r.getLong(1), r.getString(2)));
    }

    public List<Long> ids(Actor actor) {
        return query("SELECT R.ID_RESERVA FROM RESERVA R WHERE " + alcance(actor)
                + " ORDER BY R.FECHA_HORA DESC, R.ID_RESERVA DESC", parametros(actor), r -> r.getLong(1));
    }
    public boolean visible(Actor actor, long id) {
        var args = new ArrayList<Object>();
        args.add(id);
        args.addAll(Arrays.asList(parametros(actor)));
        return !query("SELECT R.ID_RESERVA FROM RESERVA R WHERE R.ID_RESERVA=? AND " + alcance(actor),
                args.toArray(), r -> r.getLong(1)).isEmpty();
    }
    private static String alcance(Actor actor) {
        return switch (actor.rol()) {
            case ADMINISTRADOR, OPERADOR -> "1=1";
            case CLIENTE -> "EXISTS (SELECT 1 FROM PERFIL_CLIENTE P WHERE P.ID_PERFIL=R.ID_PERFIL AND P.ID_USUARIO=?)";
            case ENCARGADO_PRESTADOR -> """
                (R.ID_PLAN IS NULL OR EXISTS (SELECT 1 FROM PLAN P WHERE P.ID_PLAN=R.ID_PLAN AND P.ID_PRESTADOR=?))
                AND (EXISTS (SELECT 1 FROM RESERVA_DETALLE D WHERE D.ID_RESERVA=R.ID_RESERVA)
                     OR EXISTS (SELECT 1 FROM BOLETA B WHERE B.ID_RESERVA=R.ID_RESERVA))
                AND NOT EXISTS (
                    SELECT 1 FROM (
                        SELECT ID_RESERVA, ID_ZONA FROM RESERVA_DETALLE
                        UNION ALL SELECT ID_RESERVA, ID_ZONA FROM BOLETA
                    ) L JOIN ZONA Z ON Z.ID_ZONA=L.ID_ZONA
                    JOIN FUNCION F ON F.ID_FUNCION=Z.ID_FUNCION
                    JOIN ATRACCION A ON A.ID_ATRACCION=F.ID_ATRACCION
                    WHERE L.ID_RESERVA=R.ID_RESERVA AND A.ID_PRESTADOR<>?
                )
                """;
        };
    }
    private static Object[] parametros(Actor actor) {
        if (actor.rol() == Rol.CLIENTE) return new Object[]{actor.usuario()};
        if (actor.rol() == Rol.ENCARGADO_PRESTADOR) return new Object[]{actor.prestador(), actor.prestador()};
        return new Object[0];
    }
    public Optional<Reserva> consultar(long id, boolean bloquear) {
        return uno("SELECT ID_PERFIL,ID_PLAN,FECHA_HORA,ESTADO FROM RESERVA WHERE ID_RESERVA=?"
                + (bloquear ? " FOR UPDATE WAIT 5" : ""), r -> new Reserva(id, r.getLong(1),
                numero(r, 2), r.getObject(3, LocalDateTime.class).atZone(BOGOTA).toInstant(),
                Estado.valueOf(r.getString(4)), List.of()), id).map(reserva -> new Reserva(id, reserva.perfil(),
                reserva.plan(), reserva.creada(), reserva.estado(), query(LINEAS, new Object[]{id,id}, r -> {
                    Long combo = numero(r, 3);
                    Long grupo = numero(r, 4);
                    boolean emitida = r.getInt(6) == 1;
                    return new Linea(new Seleccion(r.getLong(1), r.getLong(2), combo,
                            grupo == null ? null : grupo.intValue()),
                            r.getBigDecimal(5), emitida, r.getString(8), r.getString(9));
                })));
    }
    public long perfilDe(long usuario) {
        return uno("SELECT ID_PERFIL FROM PERFIL_CLIENTE WHERE ID_USUARIO=?", r -> r.getLong(1), usuario).orElse(0L);
    }
    public boolean existePerfil(long id) {
        return uno("SELECT ID_PERFIL FROM PERFIL_CLIENTE WHERE ID_PERFIL=?", r -> r.getLong(1), id).isPresent();
    }
    public Optional<Plan> plan(long id) {
        return uno("SELECT ID_PRESTADOR,ESTADO FROM PLAN WHERE ID_PLAN=?",
                r -> new Plan(r.getLong(1), "ACTIVO".equals(r.getString(2))), id);
    }
    public Optional<Oferta> oferta(long zona) {
        return uno("""
            SELECT F.ID_FUNCION,A.ID_ATRACCION,A.ID_PRESTADOR,Z.PRECIO,F.ESTADO,F.FECHA,F.HORA_CIERRE
            FROM ZONA Z JOIN FUNCION F ON F.ID_FUNCION=Z.ID_FUNCION
            JOIN ATRACCION A ON A.ID_ATRACCION=F.ID_ATRACCION WHERE Z.ID_ZONA=?
            """, r -> new Oferta(r.getLong(1), r.getLong(2), r.getLong(3), r.getBigDecimal(4),
                "PROGRAMADA".equals(r.getString(5)),
                r.getDate(6).toLocalDate().atTime(LocalTime.parse(r.getString(7)))), zona);
    }
    public Optional<BigDecimal> descuento(long tipo) {
        return uno("SELECT PORCENTAJE_DESCUENTO FROM TIPO_ENTRADA WHERE ID_TIPO_ENTRADA=?", r -> r.getBigDecimal(1), tipo);
    }
    public Optional<Combo> combo(long id) {
        return uno("SELECT PRECIO,DIAS_VIGENCIA,ESTADO FROM COMBO WHERE ID_COMBO=?",
                r -> new Combo(r.getBigDecimal(1), Map.of(), r.getInt(2), "ACTIVO".equals(r.getString(3))), id)
                .map(combo -> {
                    Map<Long, BigDecimal> porcentajes = new HashMap<>();
                    query("SELECT ID_ATRACCION,PORCENTAJE_PRECIO FROM COMBO_ATRACCION WHERE ID_COMBO=?",
                            new Object[]{id}, r -> porcentajes.put(r.getLong(1), r.getBigDecimal(2)));
                    return new Combo(combo.precio(), porcentajes, combo.vigencia(), combo.activo());
                });
    }
    public long insertar(long perfil, Long plan, Instant fecha) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement("""
                INSERT INTO RESERVA (ID_PERFIL,ID_PLAN,FECHA_HORA,ESTADO) VALUES (?,?,?,'PENDIENTE')
                """, new String[]{"ID_RESERVA"})) {
                bind(s, perfil, plan, LocalDateTime.ofInstant(fecha, BOGOTA));
                s.executeUpdate();
                try (var r = s.getGeneratedKeys()) {
                    if (!r.next()) throw new SQLException("Oracle no devolvió el ID de reserva.");
                    return r.getLong(1);
                }
            }
        });
    }
    public void actualizar(long id, long perfil, Long plan) {
        update("UPDATE RESERVA SET ID_PERFIL=?,ID_PLAN=? WHERE ID_RESERVA=?", perfil, plan, id);
    }
    public void reemplazarLineas(long id, List<Linea> lineas) {
        update("DELETE FROM RESERVA_DETALLE WHERE ID_RESERVA=?", id);
        ejecutar(c -> {
            try (var s = c.prepareStatement("""
                INSERT INTO RESERVA_DETALLE
                  (ID_RESERVA,LINEA,ID_ZONA,ID_TIPO_ENTRADA,ID_COMBO,GRUPO_COMBO,PRECIO_ORIENTATIVO)
                  VALUES (?,?,?,?,?,?,?)
                """)) {
                int numero = 1;
                for (var linea : lineas) {
                    var x = linea.seleccion();
                    bind(s, id, numero++, x.zona(), x.tipo(), x.combo(), x.grupo(), linea.precio());
                    s.addBatch();
                }
                s.executeBatch();
                return null;
            }
        });
    }
    public void cambiarEstado(long id, Estado estado) {
        update("UPDATE RESERVA SET ESTADO=? WHERE ID_RESERVA=? AND ESTADO='PENDIENTE'", estado.name(), id);
    }
    public List<Long> vencidas(Instant limite) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement("""
                SELECT R.ID_RESERVA FROM RESERVA R
                 WHERE R.ESTADO='PENDIENTE' AND R.FECHA_HORA<=?
                   AND NOT EXISTS (SELECT 1 FROM BOLETA B WHERE B.ID_RESERVA=R.ID_RESERVA)
                 ORDER BY R.FECHA_HORA,R.ID_RESERVA FOR UPDATE SKIP LOCKED
                """)) {
                bind(s, LocalDateTime.ofInstant(limite, BOGOTA));
                s.setFetchSize(100);
                List<Long> ids = new ArrayList<>();
                try (var r = s.executeQuery()) {
                    while (ids.size() < 100 && r.next()) ids.add(r.getLong(1));
                }
                return ids;
            }
        });
    }
    private void update(String sql, Object... args) {
        ejecutar(c -> {
            try (var s = c.prepareStatement(sql)) { bind(s, args); return s.executeUpdate(); }
        });
    }
    private <T> Optional<T> uno(String sql, Fila<T> fila, Object... args) {
        return query(sql, args, fila).stream().findFirst();
    }
    private <T> List<T> query(String sql, Object[] args, Fila<T> fila) {
        return ejecutar(c -> {
            try (var s = c.prepareStatement(sql)) {
                bind(s, args);
                try (var r = s.executeQuery()) {
                    List<T> filas = new ArrayList<>();
                    while (r.next()) filas.add(fila.leer(r));
                    return filas;
                }
            }
        });
    }
    private <T> T ejecutar(Sql<T> op) {
        var c = DataSourceUtils.getConnection(source);
        try { return op.ejecutar(c); }
        catch (SQLException ex) {
            if (ex.getErrorCode() == 2291 || ex.getErrorCode() == 54 || ex.getErrorCode() == 30006
                    || ex.getErrorCode() == 60) throw new Conflicto("Los datos cambiaron o están en uso. Reintenta.");
            var traducido = errores.translate("Acceso a reservas", null, ex);
            if (traducido != null) throw traducido;
            throw new org.springframework.jdbc.UncategorizedSQLException("Acceso a reservas", null, ex);
        } finally { DataSourceUtils.releaseConnection(c, source); }
    }
    private static Long numero(ResultSet r, int columna) throws SQLException {
        long valor = r.getLong(columna);
        return r.wasNull() ? null : valor;
    }
    private static void bind(PreparedStatement s, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) s.setNull(i + 1, Types.NUMERIC);
            else s.setObject(i + 1, args[i]);
        }
    }
    @FunctionalInterface private interface Sql<T> { T ejecutar(Connection c) throws SQLException; }
    @FunctionalInterface private interface Fila<T> { T leer(ResultSet r) throws SQLException; }
}







