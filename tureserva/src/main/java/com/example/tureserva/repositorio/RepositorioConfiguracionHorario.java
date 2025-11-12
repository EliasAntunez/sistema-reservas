package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.ConfiguracionHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioConfiguracionHorario extends JpaRepository<ConfiguracionHorario, Long> {
    
    /**
     * Busca todas las configuraciones de horario de un complejo deportivo (solo activas)
     */
    List<ConfiguracionHorario> findByComplejoDeportivoAndActivoTrue(ComplejoDeportivo complejoDeportivo);
    
    /**
     * Busca todas las configuraciones de horario de un complejo deportivo (incluyendo inactivas)
     */
    List<ConfiguracionHorario> findByComplejoDeportivo(ComplejoDeportivo complejoDeportivo);
    
    /**
     * Busca configuración por nombre y complejo (solo activas)
     */
    Optional<ConfiguracionHorario> findByNombreAndComplejoDeportivoAndActivoTrue(String nombre, ComplejoDeportivo complejoDeportivo);
    
    /**
     * Busca configuración por nombre y complejo (incluyendo inactivas)
     */
    Optional<ConfiguracionHorario> findByNombreAndComplejoDeportivo(String nombre, ComplejoDeportivo complejoDeportivo);
    
    /**
     * Verifica si existe una configuración activa con ese nombre en ese complejo
     */
    boolean existsByNombreAndComplejoDeportivoAndActivoTrue(String nombre, ComplejoDeportivo complejoDeportivo);
    
    /**
     * Verifica si existe una configuración con ese nombre en ese complejo (incluyendo inactivas)
     */
    boolean existsByNombreAndComplejoDeportivo(String nombre, ComplejoDeportivo complejoDeportivo);
    
    /**
     * Busca configuración con sus rangos cargados (solo si está activa)
     */
    @Query("SELECT c FROM ConfiguracionHorario c LEFT JOIN FETCH c.rangosHorario WHERE c.id = :id AND c.activo = true")
    Optional<ConfiguracionHorario> findByIdAndActivoTrueWithRangos(@Param("id") Long id);
    
    /**
     * Busca configuración con sus rangos cargados (para evitar LazyInitializationException)
     */
    @Query("SELECT c FROM ConfiguracionHorario c LEFT JOIN FETCH c.rangosHorario WHERE c.id = :id")
    Optional<ConfiguracionHorario> findByIdWithRangos(@Param("id") Long id);
    
    /**
     * Busca todas las configuraciones activas de un complejo con sus rangos cargados
     */
    @Query("SELECT c FROM ConfiguracionHorario c " +
           "LEFT JOIN FETCH c.rangosHorario " +
           "WHERE c.complejoDeportivo = :complejo AND c.activo = true " +
           "ORDER BY c.id")
    List<ConfiguracionHorario> findByComplejoDeportivoAndActivoTrueWithRangos(@Param("complejo") ComplejoDeportivo complejo);
    
    /**
     * Busca todas las configuraciones de un complejo con sus rangos cargados (incluyendo inactivas)
     */
    @Query("SELECT c FROM ConfiguracionHorario c " +
           "LEFT JOIN FETCH c.rangosHorario " +
           "WHERE c.complejoDeportivo = :complejo " +
           "ORDER BY c.id")
    List<ConfiguracionHorario> findByComplejoDeportivoWithRangos(@Param("complejo") ComplejoDeportivo complejo);
}
