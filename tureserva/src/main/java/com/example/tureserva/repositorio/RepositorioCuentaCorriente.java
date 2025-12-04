package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.CuentaCorriente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioCuentaCorriente extends JpaRepository<CuentaCorriente, Long> {
    
    /**
     * Encuentra la cuenta corriente de un cliente
     */
    Optional<CuentaCorriente> findByCliente(Cliente cliente);
    
    /**
     * Verifica si un cliente ya tiene una cuenta corriente
     */
    boolean existsByCliente(Cliente cliente);
    
    /**
     * Encuentra una cuenta con lock pessimista para operaciones de concurrencia
     * (evita race conditions en acreditaciones simultáneas)
     */
    @Query("SELECT c FROM CuentaCorriente c WHERE c.id = :id")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CuentaCorriente> findByIdWithLock(@Param("id") Long id);
}
