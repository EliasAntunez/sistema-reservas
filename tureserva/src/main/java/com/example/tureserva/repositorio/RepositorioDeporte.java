package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Deporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioDeporte extends JpaRepository<Deporte, Long> {
    
    /**
     * Encuentra todos los deportes activos
     */
    List<Deporte> findByActivoTrue();
    
    /**
     * Encuentra deportes por nombre (búsqueda exacta)
     */
    Deporte findByNombre(String nombre);
}
