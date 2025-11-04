package com.example.tureserva.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Salon;


@Repository
public interface RepositorioSalon extends JpaRepository<Salon, Long> {
    // Paginación de salones por complejo (solo activos)
    Page<Salon> findByComplejoDeportivoAndActivoTrue(ComplejoDeportivo complejoDeportivo, Pageable pageable);
    
    // Verificar si existe un salón con el mismo nombre en un complejo (ignorando mayúsculas/minúsculas)
    boolean existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrue(String nombre, ComplejoDeportivo complejoDeportivo);
    
    // Verificar duplicado excluyendo un ID específico (para actualización)
    boolean existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrueAndIdNot(String nombre, ComplejoDeportivo complejoDeportivo, Long id);
}
