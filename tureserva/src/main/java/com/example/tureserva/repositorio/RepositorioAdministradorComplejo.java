package com.example.tureserva.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.tureserva.modelo.AdministradorComplejo;

@Repository
public interface RepositorioAdministradorComplejo extends JpaRepository<AdministradorComplejo, Long> {
    
    // Buscar por email
    Optional<AdministradorComplejo> findByEmail(String email);

    // Buscar por DNI
    Optional<AdministradorComplejo> findByDni(String dni);

    // Encuentra todos los AdministradoresComplejo activos
    List<AdministradorComplejo> findByActivoTrue();

    // Verificar si existe por email
    boolean existsByEmail(String email);
    
    // Verificar si existe por DNI
    boolean existsByDni(String dni);
    
    // Encontrar AdministradorComplejo activo por email
    @Query("SELECT ac FROM AdministradorComplejo ac WHERE ac.email = :email AND ac.activo = true")  
    Optional<AdministradorComplejo> findByEmailAndActivoTrue(@Param("email") String email);
    
    // Contar AdministradoresComplejo activos
    long countByActivoTrue();
}
