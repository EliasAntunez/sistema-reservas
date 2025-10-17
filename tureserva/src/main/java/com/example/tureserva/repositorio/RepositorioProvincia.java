package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioProvincia extends JpaRepository<Provincia, Long> {
    List<Provincia> findByPaisId(Long paisId);
    Optional<Provincia> findByNombreAndPaisId(String nombre, Long paisId);
}