package com.example.tureserva.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.tureserva.modelo.Cancha;
import com.example.tureserva.modelo.ComplejoDeportivo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface RepositorioCancha extends JpaRepository<Cancha, Long> {
    List<Cancha> findByComplejoDeportivoAndActivoTrue(ComplejoDeportivo complejoDeportivo);

    // Paginación de canchas por complejo (solo activas)
    Page<Cancha> findByComplejoDeportivoAndActivoTrue(ComplejoDeportivo complejoDeportivo, Pageable pageable);
    
    // Verificar si existe una cancha con el mismo nombre en un complejo (ignorando mayúsculas/minúsculas)
    boolean existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrue(String nombre, ComplejoDeportivo complejoDeportivo);
    
    // Verificar duplicado excluyendo un ID específico (para actualización)
    boolean existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrueAndIdNot(String nombre, ComplejoDeportivo complejoDeportivo, Long id);
}
