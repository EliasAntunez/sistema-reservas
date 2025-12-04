package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.CuentaCorriente;
import com.example.tureserva.modelo.MovimientoCuenta;
import com.example.tureserva.modelo.OfertaFlash;
import com.example.tureserva.modelo.enums.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioMovimientoCuenta extends JpaRepository<MovimientoCuenta, Long> {
    
    /**
     * Encuentra todos los movimientos de una cuenta ordenados por fecha descendente
     */
    List<MovimientoCuenta> findByCuentaCorrienteOrderByFechaMovimientoDesc(CuentaCorriente cuenta);
    
    /**
     * Encuentra movimientos de una cuenta en un rango de fechas
     */
    @Query("SELECT m FROM MovimientoCuenta m " +
           "WHERE m.cuentaCorriente = :cuenta " +
           "AND m.fechaMovimiento BETWEEN :inicio AND :fin " +
           "ORDER BY m.fechaMovimiento DESC")
    List<MovimientoCuenta> findByCuentaAndFechaBetween(
        @Param("cuenta") CuentaCorriente cuenta,
        @Param("inicio") LocalDateTime inicio,
        @Param("fin") LocalDateTime fin
    );
    
    /**
     * Encuentra movimientos por tipo
     */
    List<MovimientoCuenta> findByCuentaCorrienteAndTipoMovimientoOrderByFechaMovimientoDesc(
        CuentaCorriente cuenta, 
        TipoMovimiento tipo
    );
    
    /**
     * Encuentra el movimiento asociado a una oferta flash específica
     */
    Optional<MovimientoCuenta> findByOfertaFlash(OfertaFlash oferta);
    
    /**
     * Cuenta los movimientos de una cuenta
     */
    long countByCuentaCorriente(CuentaCorriente cuenta);
}
