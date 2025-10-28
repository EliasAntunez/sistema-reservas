package com.example.tureserva.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.PoliticaSenia;


@Repository
public interface RepositorioPoliticaSenia extends JpaRepository<PoliticaSenia, Long> {
    List<PoliticaSenia> findByComplejoDeportivoAndActivoTrue(ComplejoDeportivo complejoDeportivo);
}
