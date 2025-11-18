package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Pago;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.TipoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioPago extends JpaRepository<Pago, Long> {
    
    /**
     * Encuentra todos los pagos de una reserva
     */
    List<Pago> findByReserva(Reserva reserva);
    
    /**
     * Encuentra todos los pagos de una reserva ordenados por fecha
     */
    List<Pago> findByReservaOrderByFechaPagoAsc(Reserva reserva);
    
    /**
     * Encuentra pagos de una reserva por tipo
     */
    List<Pago> findByReservaAndTipoPago(Reserva reserva, TipoPago tipoPago);
    
    /**
     * Busca un pago por ID de transacción externa
     */
    Optional<Pago> findByTransaccionId(String transaccionId);
    
    /**
     * Verifica si existe un pago de seña para una reserva
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Pago p " +
           "WHERE p.reserva = :reserva AND p.tipoPago = 'SENIA'")
    boolean existeSeniaParaReserva(@Param("reserva") Reserva reserva);
    
    /**
     * Verifica si existe un pago completo para una reserva
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Pago p " +
           "WHERE p.reserva = :reserva AND p.tipoPago = 'PAGO_COMPLETO'")
    boolean existePagoCompletoParaReserva(@Param("reserva") Reserva reserva);
}
