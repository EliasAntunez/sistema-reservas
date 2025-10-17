package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Localidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioLocalidad extends JpaRepository<Localidad, Long> {
    List<Localidad> findByProvinciaId(Long provinciaId);
}