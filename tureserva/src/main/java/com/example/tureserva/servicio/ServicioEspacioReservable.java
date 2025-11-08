package com.example.tureserva.servicio;

import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.repositorio.RepositorioEspacioReservable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ServicioEspacioReservable {

    private final RepositorioEspacioReservable repositorioEspacioReservable;

    public ServicioEspacioReservable(RepositorioEspacioReservable repositorioEspacioReservable) {
        this.repositorioEspacioReservable = repositorioEspacioReservable;
    }

    /**
     * Obtiene un espacio reservable por ID
     */
    public Optional<EspacioReservable> obtenerPorId(Long id) {
        return repositorioEspacioReservable.findById(id);
    }

    /**
     * Guarda o actualiza un espacio reservable
     */
    public EspacioReservable guardar(EspacioReservable espacioReservable) {
        return repositorioEspacioReservable.save(espacioReservable);
    }
}
