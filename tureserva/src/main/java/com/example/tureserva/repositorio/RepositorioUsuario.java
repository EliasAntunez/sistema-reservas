package com.example.tureserva.repositorio;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.tureserva.modelo.Usuario;


@Repository
public interface RepositorioUsuario extends JpaRepository<Usuario, Long> {
    
    //buscar por mail
    Optional<Usuario> findByEmail(String email);

    // encuentra todos los usuarios activos
    List<Usuario> findByActivoTrue();

    //existe por email
    boolean existsByEmail(String email);
    
    // Consulta optimizada: obtener tipo de usuario por email
    @Query("SELECT TYPE(u) FROM Usuario u WHERE u.email = :email")
    Optional<Class<?>> findTipoUsuarioByEmail(@Param("email") String email);
}
