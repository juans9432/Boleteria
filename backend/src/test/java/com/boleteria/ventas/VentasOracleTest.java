package com.boleteria.ventas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.reservas.application.port.in.GestionarReservas;
import com.boleteria.reservas.domain.Reserva.*;
import com.boleteria.ventas.application.port.in.ConfirmarVentas;
import com.boleteria.ventas.domain.Venta.Recotizar;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "boleteria.reservas.expiracion-activa=false")
@EnabledIfEnvironmentVariable(named = "BOLETERIA_VENTAS_QA", matches = "true")
class VentasOracleTest {
    private static final Actor ADMIN = new Actor(1, Rol.ADMINISTRADOR, null);
    @Autowired JdbcTemplate jdbc;
    @Autowired GestionarReservas reservas;
    @Autowired ConfirmarVentas ventas;
    @Autowired com.boleteria.tiposentrada.application.port.in.GestionarTiposEntrada tipos;
    final List<Long> ids = new ArrayList<>(), zonas = new ArrayList<>(), funciones = new ArrayList<>(), combos = new ArrayList<>();
    final String marca = "VQA-" + UUID.randomUUID().toString().substring(0, 12);
    long perfil, adulto, infantil, estudiante;

    @BeforeEach void contexto() {
        assertThat(jdbc.queryForObject("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL", String.class))
                .isEqualTo("BOLETERIA_RSV_QA2");
        perfil = numero("SELECT MIN(ID_PERFIL) FROM PERFIL_CLIENTE");
        adulto = numero("SELECT ID_TIPO_ENTRADA FROM TIPO_ENTRADA WHERE NOMBRE='ADULTO'");
        infantil = numero("SELECT ID_TIPO_ENTRADA FROM TIPO_ENTRADA WHERE REQUIERE_ADULTO='S'");
        estudiante = numero("SELECT ID_TIPO_ENTRADA FROM TIPO_ENTRADA WHERE NOMBRE='ESTUDIANTE'");
    }
    @AfterEach void limpiar() {
        for (long id : ids) {
            jdbc.update("DELETE FROM BOLETA WHERE ID_RESERVA=?", id);
            jdbc.update("DELETE FROM RESERVA_DETALLE WHERE ID_RESERVA=?", id);
            jdbc.update("DELETE FROM NOTIFICACION WHERE ID_RESERVA=?", id);
            jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA=?", id);
        }
        for (long id : combos) {
            jdbc.update("DELETE FROM COMBO_ATRACCION WHERE ID_COMBO=?", id);
            jdbc.update("DELETE FROM COMBO WHERE ID_COMBO=?", id);
        }
        zonas.forEach(id -> jdbc.update("DELETE FROM ZONA WHERE ID_ZONA=?", id));
        funciones.forEach(id -> jdbc.update("DELETE FROM FUNCION WHERE ID_FUNCION=?", id));
    }
    @Test void recalculaDescuentoProtegeHistoricoYReintentaSinDuplicar() {
        long zona = zona(funcion(atracciones().get(0)), 8);
        long id = pendiente(List.of(individual(zona, estudiante)));
        var anterior = ventas.cotizar(ADMIN, id);
        long otraReserva = pendiente(List.of(individual(zona, estudiante)));
        assertThatThrownBy(() -> ventas.confirmar(ADMIN, otraReserva, anterior.revision()))
                .isInstanceOf(Recotizar.class);
        assertThat(numero("SELECT COUNT(*) FROM BOLETA WHERE ID_RESERVA=?", otraReserva)).isZero();
        jdbc.update("UPDATE ZONA SET PRECIO=20000 WHERE ID_ZONA=?", zona);
        var conflicto = catchThrowableOfType(() -> ventas.confirmar(ADMIN, id, anterior.revision()), Recotizar.class);
        assertThat(conflicto).isNotNull();
        var nueva = conflicto.cotizacion();
        var descuento = jdbc.queryForObject("SELECT PORCENTAJE_DESCUENTO FROM TIPO_ENTRADA WHERE ID_TIPO_ENTRADA=?",
                BigDecimal.class, estudiante);
        assertThat(nueva.total()).isEqualByComparingTo(BigDecimal.valueOf(20000)
                .multiply(BigDecimal.valueOf(100).subtract(descuento)).movePointLeft(2));
        assertThat(numero("SELECT COUNT(*) FROM BOLETA WHERE ID_RESERVA=?", id)).isZero();
        var emitidas = ventas.confirmar(ADMIN, id, nueva.revision());
        assertThat(ventas.confirmar(ADMIN, id, anterior.revision())).isEqualTo(emitidas);
        assertThat(numero("SELECT COUNT(*) FROM NOTIFICACION WHERE ID_RESERVA=?", id)).isOne();
        assertThat(reservas.consultar(ADMIN, id).lineas()).hasSize(1).allMatch(Linea::emitida);
        long otroUsuario = numero("SELECT MIN(ID_USUARIO) FROM PERFIL_CLIENTE WHERE ID_PERFIL<>?", perfil);
        assertThatThrownBy(() -> ventas.confirmar(new Actor(otroUsuario, Rol.CLIENTE, null), id, nueva.revision()))
                .isInstanceOf(Ausente.class);
        var datos = tipos.consultar(estudiante).orElseThrow().datos();
        assertThatThrownBy(() -> tipos.actualizar(estudiante, new com.boleteria.tiposentrada.domain.TipoEntrada.Datos(
                datos.nombre(), datos.porcentajeDescuento(), "S")))
                .isInstanceOf(com.boleteria.tiposentrada.domain.TipoEntrada.SemanticaHistorica.class);
    }
    @Test void exigeAdultoEnLaMismaFuncion() {
        var atraccion = atracciones().get(0);
        long zona1 = zona(funcion(atraccion), 8), zona2 = zona(funcion(atraccion), 8);
        long invalida = pendiente(List.of(individual(zona1, infantil), individual(zona2, adulto)));
        assertThatThrownBy(() -> ventas.cotizar(ADMIN, invalida)).isInstanceOf(Conflicto.class).hasMessageContaining("ADULTO");
        long valida = pendiente(List.of(individual(zona1, infantil), individual(zona1, adulto)));
        assertThat(confirmar(valida)).hasSize(2);
    }
    @Test void serializaAforoYMaximoEntreZonasDistintas() throws Exception {
        long f = funcion(atracciones().get(0));
        long a = zona(f, 8), b = zona(f, 8);
        competir(pendiente(Collections.nCopies(4, individual(a, adulto))),
                pendiente(Collections.nCopies(4, individual(b, adulto))), "seis");
        long ultima = zona(funcion(atracciones().get(0)), 1);
        competir(pendiente(List.of(individual(ultima, adulto))),
                pendiente(List.of(individual(ultima, adulto))), "aforo");
    }
    @Test void conservaLasDosUnidadesDeUnCombo() {
        var atracciones = atracciones();
        long a = zona(funcion(atracciones.get(0)), 8), b = zona(funcion(atracciones.get(1)), 8);
        long combo = insertar("ID_COMBO", "INSERT INTO COMBO(NOMBRE,PRECIO,DIAS_VIGENCIA,ESTADO) VALUES (?,10000,3,'ACTIVO')", marca);
        combos.add(combo);
        jdbc.update("INSERT INTO COMBO_ATRACCION(ID_COMBO,ID_ATRACCION,PORCENTAJE_PRECIO) VALUES (?,?,60)", combo, atracciones.get(0));
        jdbc.update("INSERT INTO COMBO_ATRACCION(ID_COMBO,ID_ATRACCION,PORCENTAJE_PRECIO) VALUES (?,?,40)", combo, atracciones.get(1));
        long id = pendiente(List.of(new Seleccion(a,adulto,combo,1),new Seleccion(b,adulto,combo,1),
                new Seleccion(a,adulto,combo,2),new Seleccion(b,adulto,combo,2)));
        assertThat(confirmar(id)).hasSize(4);
        assertThat(numero("""
            SELECT COUNT(DISTINCT D.GRUPO_COMBO) FROM BOLETA B JOIN RESERVA_DETALLE D
              ON D.ID_RESERVA=B.ID_RESERVA AND D.LINEA=B.LINEA_RESERVA WHERE B.ID_RESERVA=?
            """, id)).isEqualTo(2);
    }
    @Test void revierteLaEmisionSiOracleRechazaLaSegundaLinea() {
        long zona = zona(funcion(atracciones().get(0)), 8);
        long id = pendiente(List.of(individual(zona, adulto), individual(zona, adulto)));
        jdbc.execute("""
            CREATE TRIGGER TRG_VENTAS_QA_FALLO BEFORE INSERT ON BOLETA FOR EACH ROW
            WHEN (NEW.ID_RESERVA = %d AND NEW.LINEA_RESERVA = 2)
            BEGIN RAISE_APPLICATION_ERROR(-20991,'Fallo controlado de prueba'); END;
            """.formatted(id));
        try {
            assertThatThrownBy(() -> confirmar(id)).isInstanceOf(RuntimeException.class);
            assertThat(numero("SELECT COUNT(*) FROM BOLETA WHERE ID_RESERVA=?", id)).isZero();
            assertThat(reservas.consultar(ADMIN, id).estado()).isEqualTo(Estado.PENDIENTE);
        } finally { jdbc.execute("DROP TRIGGER TRG_VENTAS_QA_FALLO"); }
    }
    @Test void revierteLaVentaSiFallaElAvisoObligatorio() {
        long zona = zona(funcion(atracciones().get(0)), 8);
        long id = pendiente(List.of(individual(zona, adulto)));
        jdbc.execute("""
            CREATE TRIGGER TRG_AVISO_QA_FALLO BEFORE INSERT ON NOTIFICACION FOR EACH ROW
            WHEN (NEW.ID_RESERVA = %d)
            BEGIN RAISE_APPLICATION_ERROR(-20992,'Fallo controlado de aviso'); END;
            """.formatted(id));
        try {
            assertThatThrownBy(() -> confirmar(id)).isInstanceOf(RuntimeException.class);
            assertThat(numero("SELECT COUNT(*) FROM BOLETA WHERE ID_RESERVA=?", id)).isZero();
            assertThat(numero("SELECT COUNT(*) FROM NOTIFICACION WHERE ID_RESERVA=?", id)).isZero();
            assertThat(reservas.consultar(ADMIN, id).estado()).isEqualTo(Estado.PENDIENTE);
        } finally { jdbc.execute("DROP TRIGGER TRG_AVISO_QA_FALLO"); }
    }

    @Test void confirmaUnaCompraDeMostradorSinCuenta() {
        long zona = zona(funcion(atracciones().get(0)), 8);
        perfil = numero("SELECT MIN(ID_PERFIL) FROM PERFIL_CLIENTE WHERE ID_USUARIO IS NULL");
        long id = pendiente(List.of(individual(zona, adulto)));
        assertThat(confirmar(id)).hasSize(1);
        assertThat(numero("SELECT COUNT(*) FROM NOTIFICACION WHERE ID_RESERVA=?", id)).isZero();
    }

    private void competir(long uno, long dos, String mensaje) throws Exception {
        var q1 = ventas.cotizar(ADMIN, uno);
        var q2 = ventas.cotizar(ADMIN, dos);
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> intento(uno, q1.revision(), salida));
            var b = pool.submit(() -> intento(dos, q2.revision(), salida));
            salida.countDown();
            var resultados = List.of(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
            assertThat(resultados).contains("OK");
            assertThat(resultados).anyMatch(x -> x.contains(mensaje));
        } finally { pool.shutdownNow(); }
    }
    private String intento(long id, String revision, CountDownLatch salida) throws InterruptedException {
        salida.await();
        try { ventas.confirmar(ADMIN, id, revision); return "OK"; }
        catch (Conflicto conflicto) { return conflicto.getMessage(); }
    }
    private List<com.boleteria.ventas.domain.Venta.Boleta> confirmar(long id) {
        return ventas.confirmar(ADMIN, id, ventas.cotizar(ADMIN, id).revision());
    }
    private long pendiente(List<Seleccion> lineas) {
        long id = reservas.crear(ADMIN, new Solicitud(perfil, null, lineas)).id();
        ids.add(id);
        return id;
    }
    private Seleccion individual(long zona, long tipo) { return new Seleccion(zona,tipo,null,null); }
    private List<Long> atracciones() { return jdbc.queryForList("SELECT ID_ATRACCION FROM ATRACCION ORDER BY ID_ATRACCION FETCH FIRST 2 ROWS ONLY", Long.class); }
    private long funcion(long atraccion) {
        var fecha = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1);
        long id = insertar("ID_FUNCION", """
            INSERT INTO FUNCION(ID_ATRACCION,FECHA,HORA_APERTURA,HORA_CIERRE,ESTADO)
            VALUES (?,?,'10:00','11:00','PROGRAMADA')
            """, atraccion, java.sql.Date.valueOf(fecha));
        funciones.add(id);
        return id;
    }
    private long zona(long funcion, int aforo) {
        long id = insertar("ID_ZONA", "INSERT INTO ZONA(ID_FUNCION,NOMBRE,AFORO,PRECIO) VALUES (?,?,?,10000)",
                funcion, marca + "-" + zonas.size(), aforo);
        zonas.add(id);
        return id;
    }
    private long insertar(String clave, String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var s = c.prepareStatement(sql, new String[]{clave});
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            return s;
        }, keys);
        return keys.getKey().longValue();
    }
    private long numero(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
}




