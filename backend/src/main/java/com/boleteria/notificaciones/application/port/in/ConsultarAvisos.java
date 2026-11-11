package com.boleteria.notificaciones.application.port.in;
import java.util.List;
import com.boleteria.notificaciones.domain.Aviso;

public interface ConsultarAvisos {
    List<Aviso> listar(long usuario);
    Aviso consultar(long usuario, long id);
    Aviso leer(long usuario, long id);
}

