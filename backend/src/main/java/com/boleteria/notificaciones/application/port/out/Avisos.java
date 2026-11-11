package com.boleteria.notificaciones.application.port.out;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.boleteria.notificaciones.domain.Aviso;
import com.boleteria.notificaciones.domain.Aviso.Evento;

public interface Avisos {
    List<Aviso> listar(long usuario);
    Optional<Aviso> consultar(long usuario, long id);
    boolean leer(long usuario, long id);
    void insertar(Evento evento, Instant fecha);
}

