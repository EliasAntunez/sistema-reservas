package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.ConfiguracionAlertaClima;
import com.example.tureserva.modelo.ComplejoDeportivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioConfiguracionAlertaClima extends JpaRepository<ConfiguracionAlertaClima, Long> {
    
    /**
     * Busca la configuración de alertas de un complejo específico.
     */
    Optional<ConfiguracionAlertaClima> findByComplejo(ComplejoDeportivo complejo);
    
    /**
     * Busca la configuración de alertas por ID del complejo.
     * Usa @Query porque el campo es id_complejo, no id.
     */
    @Query("SELECT c FROM ConfiguracionAlertaClima c WHERE c.complejo.id_complejo = :complejoId")
    Optional<ConfiguracionAlertaClima> findByComplejoId(@Param("complejoId") Long complejoId);
    
    /**
     * Obtiene todas las configuraciones activas.
     * Útil para el scheduler que debe revisar solo complejos con alertas activas.
     */
    List<ConfiguracionAlertaClima> findByActivoTrue();
}
