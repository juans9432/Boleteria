package com.boleteria.combos.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.boleteria.combos.application.port.out.Combos;
import com.boleteria.combos.domain.Combo;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class CombosOracle implements Combos {
    private static final String SELECT = "SELECT ID_COMBO, NOMBRE, PRECIO, DIAS_VIGENCIA, ESTADO FROM BOLETERIA_FINAL.COMBO ";
    private final DataSource dataSource;

    public CombosOracle(DataSource dataSource) { this.dataSource = dataSource; }

    private static final String VISTA = """
            SELECT C.ID_COMBO, C.NOMBRE, C.PRECIO, C.DIAS_VIGENCIA, C.ESTADO,
                   A.ID_ATRACCION, A.PORCENTAJE_PRECIO
            FROM BOLETERIA_FINAL.COMBO C
            LEFT JOIN BOLETERIA_FINAL.COMBO_ATRACCION A ON A.ID_COMBO = C.ID_COMBO
            """;

    @Override
    public List<Combo> listar() { return leerVista(null); }

    @Override
    public Optional<Combo> consultar(long id, boolean bloquear) {
        if (!bloquear) return leerVista(id).stream().findFirst();
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(SELECT + "WHERE ID_COMBO = ? FOR UPDATE WAIT 5")) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) {
                    return fila.next() ? Optional.of(leer(conexion, fila, id)) : Optional.empty();
                }
            }
        });
    }

    private List<Combo> leerVista(Long id) {
        return ejecutar(conexion -> {
            String filtro = id == null ? "" : "WHERE C.ID_COMBO = ? ";
            var combos = new ArrayList<Combo>();
            try (var consulta = conexion.prepareStatement(VISTA + filtro + "ORDER BY C.NOMBRE, C.ID_COMBO, A.ID_ATRACCION")) {
                if (id != null) consulta.setLong(1, id);
                try (var filas = consulta.executeQuery()) {
                    boolean hayFila = filas.next();
                    while (hayFila) {
                        long idCombo = filas.getLong("ID_COMBO");
                        String nombre = filas.getString("NOMBRE");
                        var precio = filas.getBigDecimal("PRECIO");
                        int dias = filas.getInt("DIAS_VIGENCIA");
                        var estado = Combo.Estado.valueOf(filas.getString("ESTADO"));
                        var atracciones = new ArrayList<Combo.Atraccion>();
                        do {
                            long idAtraccion = filas.getLong("ID_ATRACCION");
                            if (!filas.wasNull()) {
                                atracciones.add(new Combo.Atraccion(idAtraccion, filas.getBigDecimal("PORCENTAJE_PRECIO")));
                            }
                            hayFila = filas.next();
                        } while (hayFila && filas.getLong("ID_COMBO") == idCombo);
                        combos.add(new Combo(idCombo, new Combo.Datos(nombre, precio, dias, estado, atracciones)));
                    }
                }
            }
            return combos;
        });
    }

    private Combo leer(Connection conexion, java.sql.ResultSet fila, long id) throws SQLException {
        var atracciones = new ArrayList<Combo.Atraccion>();
        try (var consulta = conexion.prepareStatement("""
                SELECT ID_ATRACCION, PORCENTAJE_PRECIO FROM BOLETERIA_FINAL.COMBO_ATRACCION
                WHERE ID_COMBO = ? ORDER BY ID_ATRACCION
                """)) {
            consulta.setLong(1, id);
            try (var relaciones = consulta.executeQuery()) {
                while (relaciones.next()) {
                    atracciones.add(new Combo.Atraccion(relaciones.getLong(1), relaciones.getBigDecimal(2)));
                }
            }
        }
        return new Combo(id, new Combo.Datos(fila.getString("NOMBRE"), fila.getBigDecimal("PRECIO"),
                fila.getInt("DIAS_VIGENCIA"), Combo.Estado.valueOf(fila.getString("ESTADO")), atracciones));
    }

    @Override
    public long insertar(Combo.Datos datos) {
        return ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.COMBO (NOMBRE, PRECIO, DIAS_VIGENCIA, ESTADO) VALUES (?, ?, ?, ?)
                    """, new String[]{"ID_COMBO"})) {
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
    public void actualizar(long id, Combo.Datos datos) {
        ejecutar(conexion -> {
            try (var sentencia = conexion.prepareStatement("""
                    UPDATE BOLETERIA_FINAL.COMBO SET NOMBRE = ?, PRECIO = ?, DIAS_VIGENCIA = ?, ESTADO = ? WHERE ID_COMBO = ?
                    """)) {
                vincular(sentencia, datos);
                sentencia.setLong(5, id);
                sentencia.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void reemplazarAtracciones(long id, List<Combo.Atraccion> atracciones) {
        ejecutar(conexion -> {
            try (var borrar = conexion.prepareStatement("DELETE FROM BOLETERIA_FINAL.COMBO_ATRACCION WHERE ID_COMBO = ?")) {
                borrar.setLong(1, id);
                borrar.executeUpdate();
            }
            try (var insertar = conexion.prepareStatement("""
                    INSERT INTO BOLETERIA_FINAL.COMBO_ATRACCION (ID_COMBO, ID_ATRACCION, PORCENTAJE_PRECIO)
                    VALUES (?, ?, ?)
                    """)) {
                for (var atraccion : atracciones) {
                    insertar.setLong(1, id);
                    insertar.setLong(2, atraccion.idAtraccion());
                    insertar.setBigDecimal(3, atraccion.porcentaje());
                    insertar.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public boolean tieneBoletas(long id) {
        return ejecutar(conexion -> {
            try (var consulta = conexion.prepareStatement(
                    "SELECT 1 FROM BOLETERIA_FINAL.BOLETA WHERE ID_COMBO = ? FETCH FIRST 1 ROW ONLY")) {
                consulta.setLong(1, id);
                try (var fila = consulta.executeQuery()) { return fila.next(); }
            }
        });
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutar(conexion -> {
            try (var borrar = conexion.prepareStatement("DELETE FROM BOLETERIA_FINAL.COMBO WHERE ID_COMBO = ?")) {
                borrar.setLong(1, id);
                return borrar.executeUpdate() > 0;
            }
        });
    }

    private static void vincular(PreparedStatement sentencia, Combo.Datos datos) throws SQLException {
        sentencia.setString(1, datos.nombre());
        sentencia.setBigDecimal(2, datos.precio());
        sentencia.setInt(3, datos.diasVigencia());
        sentencia.setString(4, datos.estado().name());
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
                case 2291 -> new Combo.Invalido("Una de las atracciones seleccionadas no existe.");
                case 2292 -> new Combo.Conflicto("El combo tiene boletas asociadas. Puedes inactivarlo.");
                case 12899 -> new Combo.Invalido("El nombre supera el espacio disponible en Oracle.");
                default -> new Fallo(error);
            };
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private interface Operacion<T> { T ejecutar(Connection conexion) throws SQLException; }
}

