package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.ComplejoDeportivo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioComplejoDeportivo extends JpaRepository<ComplejoDeportivo, Long> {
    
    List<ComplejoDeportivo> findByActivoTrue();
    
    // Métodos con paginación
    Page<ComplejoDeportivo> findByActivoTrue(Pageable pageable);
    
    @Query("SELECT c FROM ComplejoDeportivo c WHERE LOWER(c.nombre_complejo) LIKE LOWER(CONCAT('%', :nombre, '%')) AND c.activo = true")
    Page<ComplejoDeportivo> findByNombreComplejoContainingIgnoreCaseAndActivoTrue(@Param("nombre") String nombre, Pageable pageable);
    
    List<ComplejoDeportivo> findByAdministradorComplejo_Id(Long administradorId);

    //obtener todos los complejos activos de un administrador
    List<ComplejoDeportivo> findByAdministradorComplejo_IdAndActivoTrue(Long administradorId);
}