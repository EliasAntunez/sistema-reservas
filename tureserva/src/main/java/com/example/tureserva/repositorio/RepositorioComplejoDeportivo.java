package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.ComplejoDeportivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioComplejoDeportivo extends JpaRepository<ComplejoDeportivo, Long> {
    
    List<ComplejoDeportivo> findByActivoTrue();
    
    List<ComplejoDeportivo> findByAdministradorComplejo_Id(Long administradorId);

    //obtener todos los complejos activos de un administrador
    List<ComplejoDeportivo> findByAdministradorComplejo_IdAndActivoTrue(Long administradorId);
}