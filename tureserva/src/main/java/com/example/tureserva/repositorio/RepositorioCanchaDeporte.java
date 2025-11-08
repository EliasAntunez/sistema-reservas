package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.CanchaDeporte;
import com.example.tureserva.modelo.Cancha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioCanchaDeporte extends JpaRepository<CanchaDeporte, Long> {
    
    /**
     * Encuentra todos los deportes asignados a una cancha específica
     */
    List<CanchaDeporte> findByCancha(Cancha cancha);
    
    /**
     * Encuentra todos los deportes asignados a una cancha por su ID
     */
    @Query("SELECT cd FROM CanchaDeporte cd WHERE cd.cancha.id = :canchaId")
    List<CanchaDeporte> findByCanchaId(@Param("canchaId") Long canchaId);
    
    /**
     * Elimina todas las asignaciones de deportes de una cancha específica
     */
    @Modifying
    @Query("DELETE FROM CanchaDeporte cd WHERE cd.cancha.id = :canchaId")
    void deleteByCanchaId(@Param("canchaId") Long canchaId);
    
    /**
     * Verifica si una cancha tiene deportes asignados
     */
    boolean existsByCanchaId(Long canchaId);
}
