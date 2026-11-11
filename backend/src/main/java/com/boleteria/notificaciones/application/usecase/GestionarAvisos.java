package com.boleteria.notificaciones.application.usecase;

import java.time.Clock;
import java.util.List;
import com.boleteria.notificaciones.application.port.in.*;
import com.boleteria.notificaciones.application.port.out.Avisos;
import com.boleteria.notificaciones.domain.Aviso;
import com.boleteria.notificaciones.domain.Aviso.Evento;

public final class GestionarAvisos implements PublicarAvisos, ConsultarAvisos {
    private final Avisos datos;
    private final Clock reloj;
    public GestionarAvisos(Avisos datos, Clock reloj) { this.datos = datos; this.reloj = reloj; }
    public void publicar(Evento evento) { datos.insertar(evento, reloj.instant()); }
    public List<Aviso> listar(long usuario) { return datos.listar(usuario); }
    public Aviso consultar(long usuario, long id) {
        return datos.consultar(usuario, id).orElseThrow(Aviso.Ausente::new);
    }
    public Aviso leer(long usuario, long id) {
        if (!datos.leer(usuario, id)) throw new Aviso.Ausente();
        return consultar(usuario, id);
    }
}

