package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.ConfiguracionHorario;
import com.example.tureserva.modelo.RangoHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface RepositorioRangoHorario extends JpaRepository<RangoHorario, Long> {
    
    /**
     * Busca todos los rangos de una configuración
     */
    List<RangoHorario> findByConfiguracionHorario(ConfiguracionHorario configuracionHorario);
    
    /**
     * Busca rangos de una configuración para un día específico
     */
    List<RangoHorario> findByConfiguracionHorarioAndDiaSemana(
            ConfiguracionHorario configuracionHorario, 
            DayOfWeek diaSemana);
    
    /**
     * Busca rangos que puedan solaparse con el nuevo rango.
     * Se usa para validación antes de insertar.
     * 
     * Casos considerados:
     * 1. Rangos normales (apertura < cierre)
     * 2. Rangos que cruzan medianoche (apertura > cierre)
     */
    @Query("SELECT r FROM RangoHorario r WHERE r.configuracionHorario = :config " +
           "AND r.diaSemana = :dia " +
           "AND r.id != :idExcluir " +
           "AND (" +
           // Caso 1: Nuevo rango no cruza medianoche
           "  (:horaApertura < :horaCierre AND (" +
           "    (r.horaApertura < r.horaCierre AND r.horaApertura < :horaCierre AND :horaApertura < r.horaCierre) OR " +
           "    (r.horaApertura >= r.horaCierre AND (:horaApertura < r.horaCierre OR :horaCierre > r.horaApertura))" +
           "  )) OR " +
           // Caso 2: Nuevo rango cruza medianoche
           "  (:horaApertura >= :horaCierre AND (" +
           "    (r.horaApertura < r.horaCierre AND (r.horaApertura < :horaCierre OR r.horaCierre > :horaApertura)) OR " +
           "    (r.horaApertura >= r.horaCierre)" +
           "  ))" +
           ")")
    List<RangoHorario> findSolapamientos(
            @Param("config") ConfiguracionHorario configuracionHorario,
            @Param("dia") DayOfWeek diaSemana,
            @Param("horaApertura") LocalTime horaApertura,
            @Param("horaCierre") LocalTime horaCierre,
            @Param("idExcluir") Long idExcluir);
    
    /**
     * Verifica si existe solapamiento (para validación simple)
     */
    default boolean existeSolapamiento(
            ConfiguracionHorario configuracionHorario,
            DayOfWeek diaSemana,
            LocalTime horaApertura,
            LocalTime horaCierre,
            Long idExcluir) {
        
        if (idExcluir == null) {
            idExcluir = -1L; // Para INSERT, no excluir ningún ID
        }
        
        List<RangoHorario> solapamientos = findSolapamientos(
                configuracionHorario, diaSemana, horaApertura, horaCierre, idExcluir);
        
        return !solapamientos.isEmpty();
    }
}
