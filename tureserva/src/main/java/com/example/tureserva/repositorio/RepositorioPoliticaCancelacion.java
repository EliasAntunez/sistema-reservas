package com.example.tureserva.repositorio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.tureserva.modelo.PoliticaCancelacion;
import com.example.tureserva.modelo.ComplejoDeportivo;

@Repository
public interface RepositorioPoliticaCancelacion extends JpaRepository<PoliticaCancelacion, Long> {
    List<PoliticaCancelacion> findByComplejoDeportivoAndActivoTrue(ComplejoDeportivo complejoDeportivo);
}
