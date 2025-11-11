package com.example.tureserva.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.tureserva.modelo.ServicioAdicional;

@Repository
public interface RepositorioServicioAdicional extends JpaRepository<ServicioAdicional, Long> {
    // JPQL explícito usando el nombre de campo real en la entidad ComplejoDeportivo (id_complejo)
    @Query("SELECT s FROM ServicioAdicional s WHERE s.complejoDeportivo.id_complejo = :idComplejo AND s.activo = true")
    List<ServicioAdicional> obtenerServiciosAdicionalesPorComplejoYActivoTrue(@Param("idComplejo") Long idComplejo);

    // Comprobaciones rápidas de existencia para evitar reliance exclusiva en excepciones de BD
    boolean existsByNombreAndComplejoDeportivo(String nombre, com.example.tureserva.modelo.ComplejoDeportivo complejoDeportivo);

    boolean existsByNombreAndComplejoDeportivoAndIdNot(String nombre, com.example.tureserva.modelo.ComplejoDeportivo complejoDeportivo, Long id);
}
