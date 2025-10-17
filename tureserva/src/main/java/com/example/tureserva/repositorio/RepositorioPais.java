package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Pais;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioPais extends JpaRepository<Pais, Long> {
    Optional<Pais> findByNombre(String nombre);
}