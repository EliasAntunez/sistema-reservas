package com.example.tureserva.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.HorarioComplejo;
import com.example.tureserva.modelo.enums.DiaSemana;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioHorarioComplejo extends JpaRepository<HorarioComplejo, Long> {

    /**
     * Obtener todos los horarios de un complejo ordenados por día y hora
     */
    @Query("SELECT h FROM HorarioComplejo h WHERE h.complejoDeportivo = :complejo ORDER BY " +
           "CASE h.diaSemana " +
           "WHEN 'LUNES' THEN 1 " +
           "WHEN 'MARTES' THEN 2 " +
           "WHEN 'MIERCOLES' THEN 3 " +
           "WHEN 'JUEVES' THEN 4 " +
           "WHEN 'VIERNES' THEN 5 " +
           "WHEN 'SABADO' THEN 6 " +
           "WHEN 'DOMINGO' THEN 7 " +
           "END, h.horaApertura")
    List<HorarioComplejo> findByComplejoDeportivo(@Param("complejo") ComplejoDeportivo complejo);

    /**
     * Obtener TODOS los horarios de un complejo para un día específico (múltiples rangos)
     */
    @Query("SELECT h FROM HorarioComplejo h WHERE h.complejoDeportivo = :complejo AND h.diaSemana = :dia ORDER BY h.horaApertura")
    List<HorarioComplejo> findAllByComplejoDeportivoAndDiaSemana(@Param("complejo") ComplejoDeportivo complejo,
                                                                  @Param("dia") DiaSemana dia);

    /**
     * Obtener horario específico de un complejo para un día determinado (primer resultado)
     */
    @Query("SELECT h FROM HorarioComplejo h WHERE h.complejoDeportivo = :complejo AND h.diaSemana = :dia ORDER BY h.horaApertura LIMIT 1")
    Optional<HorarioComplejo> findByComplejoDeportivoAndDiaSemana(@Param("complejo") ComplejoDeportivo complejo,
                                                                   @Param("dia") DiaSemana dia);

    /**
     * Verificar si existe un horario para un complejo en un día específico
     */
    @Query("SELECT COUNT(h) > 0 FROM HorarioComplejo h WHERE h.complejoDeportivo = :complejo AND h.diaSemana = :dia")
    boolean existsByComplejoDeportivoAndDiaSemana(@Param("complejo") ComplejoDeportivo complejo,
                                                   @Param("dia") DiaSemana dia);

    /**
     * Contar horarios configurados para un complejo
     */
    @Query("SELECT COUNT(h) FROM HorarioComplejo h WHERE h.complejoDeportivo = :complejo")
    long countByComplejoDeportivo(@Param("complejo") ComplejoDeportivo complejo);
}
