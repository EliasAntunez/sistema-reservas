package com.example.tureserva.servicio;

import com.example.tureserva.modelo.Deporte;
import com.example.tureserva.repositorio.RepositorioDeporte;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ServicioDeporte {

    private final RepositorioDeporte repositorioDeporte;

    public ServicioDeporte(RepositorioDeporte repositorioDeporte) {
        this.repositorioDeporte = repositorioDeporte;
    }

    /**
     * Lista todos los deportes
     */
    public List<Deporte> listarTodos() {
        return repositorioDeporte.findAll();
    }

    /**
     * Lista solo los deportes activos
     */
    public List<Deporte> listarActivos() {
        return repositorioDeporte.findByActivoTrue();
    }

    /**
     * Obtiene un deporte por ID
     */
    public Optional<Deporte> obtenerPorId(Long id) {
        return repositorioDeporte.findById(id);
    }

    /**
     * Guarda un deporte (crear o actualizar)
     */
    public Deporte guardar(Deporte deporte) {
        return repositorioDeporte.save(deporte);
    }

    /**
     * Elimina un deporte por ID
     */
    public void eliminar(Long id) {
        repositorioDeporte.deleteById(id);
    }
}
