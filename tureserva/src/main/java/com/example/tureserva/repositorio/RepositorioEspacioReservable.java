package com.example.tureserva.repositorio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.ConfiguracionHorario;
import java.util.Optional;

@Repository
public interface RepositorioEspacioReservable extends JpaRepository<EspacioReservable, Long> {
    List<EspacioReservable> findByComplejoDeportivo(ComplejoDeportivo complejoDeportivo);
    List<EspacioReservable> findByHorarioPersonalizado(ConfiguracionHorario horarioPersonalizado);
    
    /**
     * Busca espacios de un complejo con todas sus relaciones LAZY precargadas.
     * Esto evita LazyInitializationException al acceder a relaciones fuera de la transacción.
     */
    @Query("SELECT DISTINCT e FROM EspacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo " +
           "LEFT JOIN FETCH e.horarioPersonalizado " +
           "LEFT JOIN FETCH e.politicaSenia " +
           "WHERE e.complejoDeportivo = :complejo")
    List<EspacioReservable> findByComplejoDeportivoWithRelations(@Param("complejo") ComplejoDeportivo complejo);

    /**
     * Busca un EspacioReservable por su ID y trae (JOIN FETCH)
     * todas las configuraciones de horario asociadas (master, canchas, salones)
     * y, a su vez, los rangos (días/horas) de cada una de esas configuraciones.
     * * Esto resuelve todos los problemas de LazyInitializationException
     * al cargar todo lo necesario en una sola consulta.
     */
    @Query("SELECT er FROM EspacioReservable er " +
           "LEFT JOIN FETCH er.complejoDeportivo cd " +
           // --- Fetch del Horario Master y sus Rangos ---
           "LEFT JOIN FETCH cd.configuracionHorarioMaster hm " +
           "LEFT JOIN FETCH hm.rangosHorario " +
           // --- Fetch del Horario Canchas y sus Rangos ---
           "LEFT JOIN FETCH cd.configuracionHorarioCanchas hc " +
           "LEFT JOIN FETCH hc.rangosHorario " +
           // --- Fetch del Horario Salones y sus Rangos ---
           "LEFT JOIN FETCH cd.configuracionHorarioSalones hs " +
           "LEFT JOIN FETCH hs.rangosHorario " +
           "WHERE er.id = :espacioId")
    Optional<EspacioReservable> findByIdWithFullHorarios(@Param("espacioId") Long espacioId);
}
