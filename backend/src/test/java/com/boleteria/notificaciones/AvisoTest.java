package com.boleteria.notificaciones;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.boleteria.notificaciones.domain.Aviso.Evento;
import static org.assertj.core.api.Assertions.*;

class AvisoTest {
    @Test void distingueLasEntradasAnuladasYVigentesConTextoAcotado() {
        String codigo = "x".repeat(36);
        var aviso = Evento.anulacion(1, "FUNCION:2", "Función cancelada",
                Collections.nCopies(100, codigo), List.of("VIGENTE-1"));
        assertThat(aviso.mensaje()).contains("Anuladas en esta operación: 100", "VIGENTE-1").hasSizeLessThan(500);
        assertThat(aviso.mensaje()).doesNotContain("reembolso");
    }
}

