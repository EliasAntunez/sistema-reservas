package com.example.tureserva.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.tureserva.modelo.SuperAdministrador;

@Repository
public interface RepositorioSuperAdministrador extends JpaRepository<SuperAdministrador, Long> {
    
    // Buscar por email
    Optional<SuperAdministrador> findByEmail(String email);

    // Buscar por DNI
    Optional<SuperAdministrador> findByDni(String dni);

    // Encuentra todos los SuperAdministradores activos
    List<SuperAdministrador> findByActivoTrue();

    // Verificar si existe por email
    boolean existsByEmail(String email);
    
    // Verificar si existe por DNI
    boolean existsByDni(String dni);
    
    // Encontrar SuperAdministrador activo por email
    @Query("SELECT sa FROM SuperAdministrador sa WHERE sa.email = :email AND sa.activo = true")
    Optional<SuperAdministrador> findByEmailAndActivoTrue(@Param("email") String email);
    
    // Contar SuperAdministradores activos
    long countByActivoTrue();
}