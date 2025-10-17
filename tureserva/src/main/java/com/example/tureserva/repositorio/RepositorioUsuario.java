package com.example.tureserva.repositorio;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.tureserva.modelo.Usuario;


@Repository
public interface RepositorioUsuario extends JpaRepository<Usuario, Long> {
    
    //buscar por mail
    Optional<Usuario> findByEmail(String email);

    // encuentra todos los usuarios activos
    List<Usuario> findByActivoTrue();

    //existe por email
    boolean existsByEmail(String email);
}
