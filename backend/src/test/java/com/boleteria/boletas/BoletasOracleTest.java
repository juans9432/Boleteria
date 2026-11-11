package com.boleteria.boletas;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.boleteria.boletas.application.port.in.GestionarBoletas;
import com.boleteria.boletas.domain.Boleta.*;
import com.boleteria.identidad.domain.Rol;
import com.boleteria.reservas.domain.Reserva.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="boleteria.reservas.expiracion-activa=false")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named="BOLETERIA_BOLETAS_QA",matches="true")
class BoletasOracleTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Tiempo {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock relojDePrueba() { return Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired GestionarBoletas boletas;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    final List<Long> funciones = new ArrayList<>(), zonas = new ArrayList<>(), emitidas = new ArrayList<>();
    final String correo="boletas-"+UUID.randomUUID()+"@boletas.invalid";
    final String clave=UUID.randomUUID().toString();
    long reserva, perfil, usuario, tipo;
    LocalDate hoy;
    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL",String.class))
                .isEqualTo("BOLETERIA_RSV_QA2");
        hoy=LocalDate.of(2026,11,10);
        jdbc.update("""
            INSERT INTO USUARIO_SISTEMA(CORREO,CLAVE_HASH,ESTADO,ID_ROL)
            SELECT ?,?,'ACTIVO',ID_ROL FROM ROL WHERE NOMBRE='CLIENTE'
            """,correo,encoder.encode(clave));
        usuario=numero("SELECT ID_USUARIO FROM USUARIO_SISTEMA WHERE CORREO=?",correo);
        perfil=insertar("ID_PERFIL","INSERT INTO PERFIL_CLIENTE(DOCUMENTO,NOMBRE,ID_USUARIO) VALUES (?,'Temporal',?)",correo.substring(0,15),usuario);
        reserva=insertar("ID_RESERVA","INSERT INTO RESERVA(ID_PERFIL,ESTADO) VALUES (?,'CONFIRMADA')",perfil);
        tipo=numero("SELECT MIN(ID_TIPO_ENTRADA) FROM TIPO_ENTRADA");
    }
    @AfterEach void limpiar() {
        emitidas.forEach(id -> jdbc.update("DELETE FROM BOLETA WHERE ID_BOLETA=?",id));
        if(reserva>0) {
            jdbc.update("DELETE FROM RESERVA_DETALLE WHERE ID_RESERVA=?",reserva);
            jdbc.update("DELETE FROM NOTIFICACION WHERE ID_RESERVA=?", reserva);
            jdbc.update("DELETE FROM RESERVA WHERE ID_RESERVA=?",reserva);
        }
        if (comboTemporal > 0) jdbc.update("DELETE FROM COMBO WHERE ID_COMBO=?",comboTemporal);
        zonas.forEach(id -> jdbc.update("DELETE FROM ZONA WHERE ID_ZONA=?",id));
        funciones.forEach(id -> jdbc.update("DELETE FROM FUNCION WHERE ID_FUNCION=?",id));
        if(perfil>0) jdbc.update("DELETE FROM PERFIL_CLIENTE WHERE ID_PERFIL=?",perfil);
        if(usuario>0) jdbc.update("DELETE FROM USUARIO_SISTEMA WHERE ID_USUARIO=?",usuario);
    }
    @Test void consultaPorCodigoEIdYSeparaAlEncargadoDeUnaCompraMixta() throws Exception {
        var propias=jdbc.queryForList("SELECT MIN(ID_ATRACCION) FROM ATRACCION GROUP BY ID_PRESTADOR ORDER BY ID_PRESTADOR",Long.class);
        long propio=emitir(propias.get(0),hoy,"PROGRAMADA","VENDIDA");
        long ajeno=emitir(propias.get(1),hoy,"PROGRAMADA","VENDIDA");
        var encargado=new Actor(1,Rol.ENCARGADO_PRESTADOR,numero("SELECT ID_PRESTADOR FROM ATRACCION WHERE ID_ATRACCION=?",propias.get(0)));
        var cliente=new Actor(usuario,Rol.CLIENTE,null);
        assertThat(boletas.listar(encargado)).extracting(com.boleteria.boletas.domain.Boleta::id).contains(propio).doesNotContain(ajeno);
        assertThat(boletas.consultar(encargado,propio).reserva()).isEqualTo(reserva);
        assertThatThrownBy(() -> boletas.usar(encargado,ajeno)).isInstanceOf(Ausente.class);
        String codigo=boletas.consultar(cliente,propio).codigo();
        var sesion=login();
        mvc.perform(get("/api/boletas/codigo").param("valor",codigo).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propio));
        mvc.perform(get("/api/boletas/codigo").param("valor","inexistente").session(sesion)).andExpect(status().isNotFound());
        mvc.perform(get("/api/boletas/{id}",numero("SELECT MIN(ID_BOLETA) FROM BOLETA WHERE ID_RESERVA<>?",reserva))
                .session(sesion)).andExpect(status().isNotFound());
        mvc.perform(post("/api/boletas/{id}/usar",propio).session(sesion).header("X-CSRF-TOKEN",token(sesion)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/boletas")).andExpect(status().isUnauthorized());
    }
    @Test void serializaElDobleUsoYConservaLosDatosEmitidos() throws Exception {
        long id=emitir(numero("SELECT MIN(ID_ATRACCION) FROM ATRACCION"),hoy,"PROGRAMADA","VENDIDA");
        var operador=new Actor(1,Rol.OPERADOR,null);
        var anterior=boletas.consultar(operador,id);
        var inicio=new CountDownLatch(1);
        Callable<Boolean> usar=() -> {
            inicio.await();
            try { boletas.usar(operador,id); return true; } catch(Conflicto esperado) { return false; }
        };
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(usar); var b=pool.submit(usar); inicio.countDown();
            assertThat(List.of(a.get(12,TimeUnit.SECONDS),b.get(12,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); }
        var usada=boletas.consultar(operador,id);
        assertThat(usada.estado()).isEqualTo(Estado.USADA);
        assertThat(usada).usingRecursiveComparison().ignoringFields("estado").isEqualTo(anterior);
        assertThat(usada.lineaReserva()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM RESERVA WHERE ID_RESERVA=?",String.class,reserva)).isEqualTo("CONFIRMADA");
    }
    @Test void rechazaAnuladasFuncionesInactivasYFechaAjena() {
        long a=numero("SELECT MIN(ID_ATRACCION) FROM ATRACCION");
        var admin=new Actor(1,Rol.ADMINISTRADOR,null);
        for(long id : List.of(emitir(a,hoy,"PROGRAMADA","ANULADA"),emitir(a,hoy,"CANCELADA","VENDIDA"),
                emitir(a,hoy,"REALIZADA","VENDIDA"),emitir(a,hoy.plusDays(1),"PROGRAMADA","VENDIDA"))) {
            assertThatThrownBy(() -> boletas.usar(admin,id)).isInstanceOf(Conflicto.class);
            assertThat(boletas.consultar(admin,id).estado()).isNotEqualTo(Estado.USADA);
        }
    }
    @Autowired com.boleteria.cancelaciones.application.port.in.GestionarCancelaciones cancelaciones;
    long comboTemporal;

    @Test void anulaSoloLaFuncionPropiaEnUnaReservaMixta() {
        var atracciones = jdbc.queryForList("SELECT MIN(ID_ATRACCION) FROM ATRACCION GROUP BY ID_PRESTADOR ORDER BY ID_PRESTADOR", Long.class);
        long a = emitir(atracciones.get(0),hoy,"PROGRAMADA","VENDIDA");
        long b = emitir(atracciones.get(1),hoy,"PROGRAMADA","VENDIDA");
        asignarCombo();
        long prestador = numero("SELECT ID_PRESTADOR FROM ATRACCION WHERE ID_ATRACCION=?",atracciones.get(0));
        var encargado = new Actor(1,Rol.ENCARGADO_PRESTADOR,prestador);
        assertThatThrownBy(() -> cancelaciones.cancelarReserva(encargado,reserva)).isInstanceOf(Ausente.class);
        assertThatThrownBy(() -> cancelaciones.cancelarFuncion(new Actor(1,Rol.OPERADOR,null),funciones.get(0))).isInstanceOf(Denegado.class);
        assertThatThrownBy(() -> cancelaciones.anularCombo(encargado,a)).isInstanceOf(Ausente.class);
        cancelaciones.cancelarFuncion(encargado,funciones.get(0));
        assertThat(numero("SELECT COUNT(*) FROM NOTIFICACION WHERE ID_RESERVA=?",reserva)).isOne();
        assertThat(jdbc.queryForObject("SELECT MENSAJE FROM NOTIFICACION WHERE ID_RESERVA=?",String.class,reserva))
                .contains(boletas.consultar(new Actor(1,Rol.ADMINISTRADOR,null),b).codigo());
        assertThat(boletas.consultar(encargado,a).estado()).isEqualTo(Estado.ANULADA);
        var admin = new Actor(1,Rol.ADMINISTRADOR,null);
        assertThat(boletas.consultar(admin,b).estado()).isEqualTo(Estado.VENDIDA);
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM RESERVA WHERE ID_RESERVA=?",String.class,reserva)).isEqualTo("CONFIRMADA");
        cancelaciones.cancelarReserva(admin,reserva);
        assertThat(boletas.consultar(admin,b).estado()).isEqualTo(Estado.ANULADA);
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM RESERVA WHERE ID_RESERVA=?",String.class,reserva)).isEqualTo("CANCELADA");
    }

    @Test void protegeAcompanamientoYAnulaLaUnidadCompleta() throws Exception {
        long a = numero("SELECT MIN(ID_ATRACCION) FROM ATRACCION");
        long adulto = numero("SELECT ID_TIPO_ENTRADA FROM TIPO_ENTRADA WHERE NOMBRE='ADULTO'");
        long menor = numero("SELECT MIN(ID_TIPO_ENTRADA) FROM TIPO_ENTRADA WHERE REQUIERE_ADULTO='S'");
        long primera = emitir(a,hoy,"PROGRAMADA","VENDIDA");
        jdbc.update("UPDATE BOLETA SET ID_TIPO_ENTRADA=? WHERE ID_BOLETA=?",adulto,primera);
        long segunda = agregarBoleta(zonas.get(0),menor,"VENDIDA");
        var admin = new Actor(1,Rol.ADMINISTRADOR,null);
        assertThatThrownBy(() -> cancelaciones.anularBoleta(admin,primera)).isInstanceOf(Conflicto.class);
        var sesion = login();
        mvc.perform(post("/api/reservas/{id}/cancelar-confirmada",reserva).session(sesion).header("X-CSRF-TOKEN",token(sesion)))
                .andExpect(status().isForbidden());
        asignarCombo();
        assertThatThrownBy(() -> cancelaciones.anularBoleta(admin,primera)).isInstanceOf(Conflicto.class);
        cancelaciones.anularCombo(admin,primera);
        assertThat(boletas.consultar(admin,primera).estado()).isEqualTo(Estado.ANULADA);
        assertThat(boletas.consultar(admin,segunda).estado()).isEqualTo(Estado.ANULADA);
    }

    @Test void usoYAnulacionCompitenPorLaMismaBoleta() throws Exception {
        long id = emitir(numero("SELECT MIN(ID_ATRACCION) FROM ATRACCION"),hoy,"PROGRAMADA","VENDIDA");
        var admin = new Actor(1,Rol.ADMINISTRADOR,null);
        var inicial = boletas.consultar(admin,id);
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var uso = pool.submit(() -> intentar(salida, () -> boletas.usar(admin,id)));
            var anulacion = pool.submit(() -> intentar(salida, () -> cancelaciones.anularBoleta(admin,id)));
            salida.countDown();
            assertThat(List.of(uso.get(12,TimeUnit.SECONDS),anulacion.get(12,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); }
        assertThat(boletas.consultar(admin,id)).usingRecursiveComparison().ignoringFields("estado").isEqualTo(inicial);
    }

    @Test void rechazaTodaCancelacionConBoletasUsadas() {
        long a = numero("SELECT MIN(ID_ATRACCION) FROM ATRACCION");
        long usada = emitir(a,hoy,"PROGRAMADA","USADA");
        long vendida = agregarBoleta(zonas.get(0),tipo,"VENDIDA");
        var admin = new Actor(1,Rol.ADMINISTRADOR,null);
        assertThatThrownBy(() -> cancelaciones.anularBoleta(admin,usada)).isInstanceOf(Conflicto.class);
        assertThatThrownBy(() -> cancelaciones.cancelarFuncion(admin,funciones.get(0))).isInstanceOf(Conflicto.class);
        assertThatThrownBy(() -> cancelaciones.cancelarReserva(admin,reserva)).isInstanceOf(Conflicto.class);
        assertThat(boletas.consultar(admin,vendida).estado()).isEqualTo(Estado.VENDIDA);
    }

    private void asignarCombo() {
        comboTemporal = insertar("ID_COMBO","INSERT INTO COMBO(NOMBRE,PRECIO,DIAS_VIGENCIA,ESTADO) VALUES (?,10000,1,'ACTIVO')",correo.substring(0,25));
        jdbc.update("UPDATE RESERVA_DETALLE SET ID_COMBO=?,GRUPO_COMBO=1 WHERE ID_RESERVA=?",comboTemporal,reserva);
        jdbc.update("UPDATE BOLETA SET ID_COMBO=? WHERE ID_RESERVA=?",comboTemporal,reserva);
    }

    @Autowired com.boleteria.notificaciones.application.port.in.PublicarAvisos publicarAvisos;
    @Autowired org.springframework.transaction.PlatformTransactionManager transacciones;

    @Test void deduplicaEnOracleYLimitaLaBandejaAlDestinatario() throws Exception {
        var aviso = com.boleteria.notificaciones.domain.Aviso.Evento.confirmada(reserva, 2);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transacciones);
        var pool = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        Callable<Void> publicar = () -> {
            inicio.await();
            tx.executeWithoutResult(status -> publicarAvisos.publicar(aviso));
            return null;
        };
        try {
            var a = pool.submit(publicar);
            var b = pool.submit(publicar);
            inicio.countDown();
            a.get(12, TimeUnit.SECONDS);
            b.get(12, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(numero("SELECT COUNT(*) FROM NOTIFICACION WHERE ID_RESERVA=?", reserva)).isOne();
        long id = numero("SELECT ID_NOTIFICACION FROM NOTIFICACION WHERE ID_RESERVA=?", reserva);
        long ajeno = numero("SELECT MIN(ID_NOTIFICACION) FROM NOTIFICACION WHERE ID_USUARIO<>?", usuario);
        var sesion = login();
        mvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notificaciones").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/notificaciones/{id}", ajeno).session(sesion)).andExpect(status().isNotFound());
        mvc.perform(post("/api/notificaciones/{id}/leer", ajeno).session(sesion).header("X-CSRF-TOKEN", token(sesion)))
                .andExpect(status().isNotFound());
        for (int i=0; i<2; i++) {
            mvc.perform(post("/api/notificaciones/{id}/leer", id).session(sesion).header("X-CSRF-TOKEN", token(sesion)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true));
        }
    }

    private boolean intentar(CountDownLatch salida, Runnable accion) throws InterruptedException {
        salida.await();
        try { accion.run(); return true; } catch (Conflicto conflicto) { return false; }
    }

    private long emitir(long atraccion,LocalDate fecha,String funcion,String estado) {
        long f=insertar("ID_FUNCION","INSERT INTO FUNCION(ID_ATRACCION,FECHA,HORA_APERTURA,HORA_CIERRE,ESTADO) VALUES (?,?,'00:00','23:59',?)",
                atraccion,java.sql.Date.valueOf(fecha),funcion);
        funciones.add(f);
        long z=insertar("ID_ZONA","INSERT INTO ZONA(ID_FUNCION,NOMBRE,AFORO,PRECIO) VALUES (?,'Zona prueba',4,12000)",f);
        zonas.add(z);
        return agregarBoleta(z,tipo,estado);
    }
    private long agregarBoleta(long z,long tipo,String estado) {
        int linea=emitidas.size()+1;
        jdbc.update("INSERT INTO RESERVA_DETALLE(ID_RESERVA,LINEA,ID_ZONA,ID_TIPO_ENTRADA,PRECIO_ORIENTATIVO) VALUES (?,?,?,?,12000)",reserva,linea,z,tipo);
        long id=insertar("ID_BOLETA","INSERT INTO BOLETA(CODIGO,ESTADO,PRECIO_PAGADO,ID_RESERVA,ID_ZONA,ID_TIPO_ENTRADA,LINEA_RESERVA) VALUES (?,?,12000,?,?,?,?)",
                UUID.randomUUID().toString(),estado,reserva,z,tipo,linea);
        emitidas.add(id);
        return id;
    }
    private long insertar(String clave,String sql,Object... args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c -> {
            var s=c.prepareStatement(sql,new String[]{clave});
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            return s;
        },keys);
        return keys.getKey().longValue();
    }
    private long numero(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    private MockHttpSession login() throws Exception {
        var s=new MockHttpSession();
        return (MockHttpSession)mvc.perform(post("/api/auth/login").session(s).header("X-CSRF-TOKEN",token(s))
                .param("correo",correo).param("clave",clave)).andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private String token(MockHttpSession s) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(s)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}






