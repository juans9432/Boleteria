package com.boleteria.notificaciones.application.port.in;
import com.boleteria.notificaciones.domain.Aviso.Evento;

public interface PublicarAvisos {
    void publicar(Evento evento);
}

