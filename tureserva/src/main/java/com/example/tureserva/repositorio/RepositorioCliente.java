package com.example.tureserva.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.tureserva.modelo.Cliente;

@Repository
public interface RepositorioCliente extends JpaRepository<Cliente, Long> {
    
    // Buscar por email
    Optional<Cliente> findByEmail(String email);

    // Encuentra todos los clientes activos
    List<Cliente> findByActivoTrue();

    // Verificar si existe por email
    boolean existsByEmail(String email);
    
    // Encontrar cliente activo por email (evitar clientes dados de baja)
    @Query("SELECT c FROM Cliente c WHERE c.email = :email AND c.activo = true")
    Optional<Cliente> findByEmailAndActivoTrue(@Param("email") String email);
    
    // Contar clientes activos (útil para estadísticas)
    long countByActivoTrue();
}
