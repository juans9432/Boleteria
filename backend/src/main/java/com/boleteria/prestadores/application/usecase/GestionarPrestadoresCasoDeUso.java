package com.boleteria.prestadores.application.usecase;

import java.util.List;
import java.util.Optional;
import com.boleteria.prestadores.application.port.in.GestionarPrestadores;
import com.boleteria.prestadores.application.port.out.Prestadores;
import com.boleteria.prestadores.domain.DatosPrestador;
import com.boleteria.prestadores.domain.Prestador;

public class GestionarPrestadoresCasoDeUso implements GestionarPrestadores {
    private final Prestadores prestadores;

    public GestionarPrestadoresCasoDeUso(Prestadores prestadores) {
        this.prestadores = prestadores;
    }

    @Override
    public List<Prestador> listar(Acceso acceso) {
        if (acceso.administrador()) return prestadores.listar();
        if (acceso.prestadorAsignado() == null) throw new Denegado();
        return prestadores.consultar(acceso.prestadorAsignado()).stream().toList();
    }

    @Override
    public Optional<Prestador> consultar(Acceso acceso, long id) {
        exigirAlcance(acceso, id);
        return prestadores.consultar(id);
    }

    @Override
    public Prestador crear(Acceso acceso, DatosPrestador datos) {
        exigirAdministrador(acceso);
        return prestadores.crear(datos);
    }

    @Override
    public Optional<Prestador> actualizar(Acceso acceso, long id, DatosPrestador datos) {
        exigirAlcance(acceso, id);
        return prestadores.actualizar(id, datos);
    }

    @Override
    public boolean eliminar(Acceso acceso, long id) {
        exigirAdministrador(acceso);
        validarId(id);
        return prestadores.eliminar(id);
    }

    private static void exigirAdministrador(Acceso acceso) {
        if (!acceso.administrador()) throw new Denegado();
    }

    private static void exigirAlcance(Acceso acceso, long id) {
        validarId(id);
        if (!acceso.administrador() && (acceso.prestadorAsignado() == null || acceso.prestadorAsignado() != id)) {
            throw new Denegado();
        }
    }

    private static void validarId(long id) {
        if (id < 1) throw new DatosPrestador.Invalido("El ID debe ser positivo.");
    }
}
