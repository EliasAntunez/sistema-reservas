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
        * Buscar pago por preference id (Mercado Pago)
        */
       Optional<Pago> findByPreferenceId(String preferenceId);
    
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
    
    /**
     * Obtiene reporte financiero basado en pagos reales (no en estado de reserva).
     * Agrupa por espacio y suma los montos de todos los pagos realizados.
     * Usa fechaPago si existe, sino usa fechaCreacion como fallback (para pagos manuales antiguos).
     */
    @Query("SELECT new com.example.tureserva.servicio.dto.ReporteFinancieroDTO(" +
           "e.nombre, " +
           "CASE WHEN TYPE(e) = com.example.tureserva.modelo.Cancha THEN 'CANCHA' " +
           "     WHEN TYPE(e) = com.example.tureserva.modelo.Salon THEN 'SALON' " +
           "     ELSE 'N/A' END, " +
           "COUNT(DISTINCT p.id), " +
           "COALESCE(SUM(p.monto), 0)) " +
           "FROM Pago p " +
           "JOIN p.reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "JOIN e.complejoDeportivo c " +
           "WHERE CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) BETWEEN :inicio AND :fin " +
           "AND (:complejoId IS NULL OR c.id = :complejoId) " +
           "GROUP BY e.nombre, CASE WHEN TYPE(e) = com.example.tureserva.modelo.Cancha THEN 'CANCHA' " +
           "                         WHEN TYPE(e) = com.example.tureserva.modelo.Salon THEN 'SALON' " +
           "                         ELSE 'N/A' END " +
           "ORDER BY SUM(p.monto) DESC")
    List<com.example.tureserva.servicio.dto.ReporteFinancieroDTO> obtenerReporteFinancieroPorPagos(
        @Param("inicio") java.time.LocalDate inicio,
        @Param("fin") java.time.LocalDate fin,
        @Param("complejoId") Long complejoId);
    
    /**
     * Obtiene reporte financiero basado en pagos reales para múltiples complejos.
     * Útil para admins con varios complejos que quieren ver todos sus datos agregados.
     * Usa fechaPago si existe, sino usa fechaCreacion como fallback (para pagos manuales antiguos).
     */
    @Query("SELECT new com.example.tureserva.servicio.dto.ReporteFinancieroDTO(" +
           "e.nombre, " +
           "CASE WHEN TYPE(e) = com.example.tureserva.modelo.Cancha THEN 'CANCHA' " +
           "     WHEN TYPE(e) = com.example.tureserva.modelo.Salon THEN 'SALON' " +
           "     ELSE 'N/A' END, " +
           "COUNT(DISTINCT p.id), " +
           "COALESCE(SUM(p.monto), 0)) " +
           "FROM Pago p " +
           "JOIN p.reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "JOIN e.complejoDeportivo c " +
           "WHERE CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) BETWEEN :inicio AND :fin " +
           "AND c.id IN :complejoIds " +
           "GROUP BY e.nombre, CASE WHEN TYPE(e) = com.example.tureserva.modelo.Cancha THEN 'CANCHA' " +
           "                         WHEN TYPE(e) = com.example.tureserva.modelo.Salon THEN 'SALON' " +
           "                         ELSE 'N/A' END " +
           "ORDER BY SUM(p.monto) DESC")
    List<com.example.tureserva.servicio.dto.ReporteFinancieroDTO> obtenerReporteFinancieroPorPagosMultiplesComplejos(
        @Param("inicio") java.time.LocalDate inicio,
        @Param("fin") java.time.LocalDate fin,
        @Param("complejoIds") List<Long> complejoIds);
    
    /**
     * Obtiene reporte financiero temporal (agrupado por fecha) para un complejo específico.
     * Usado para gráficos de evolución temporal con eje X = tiempo.
     */
    @Query("SELECT new com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO(" +
           "CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date), " +
           "COUNT(DISTINCT p.id), " +
           "COALESCE(SUM(p.monto), 0)) " +
           "FROM Pago p " +
           "JOIN p.reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "JOIN e.complejoDeportivo c " +
           "WHERE CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) BETWEEN :inicio AND :fin " +
           "AND (:complejoId IS NULL OR c.id = :complejoId) " +
           "GROUP BY CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) " +
           "ORDER BY CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) ASC")
    List<com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO> obtenerReporteFinancieroTemporal(
        @Param("inicio") java.time.LocalDate inicio,
        @Param("fin") java.time.LocalDate fin,
        @Param("complejoId") Long complejoId);
    
    /**
     * Obtiene reporte financiero temporal (agrupado por fecha) para múltiples complejos.
     * Usado para gráficos de evolución temporal con eje X = tiempo.
     */
    @Query("SELECT new com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO(" +
           "CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date), " +
           "COUNT(DISTINCT p.id), " +
           "COALESCE(SUM(p.monto), 0)) " +
           "FROM Pago p " +
           "JOIN p.reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "JOIN e.complejoDeportivo c " +
           "WHERE CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) BETWEEN :inicio AND :fin " +
           "AND c.id IN :complejoIds " +
           "GROUP BY CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) " +
           "ORDER BY CAST(COALESCE(p.fechaPago, p.fechaCreacion) AS date) ASC")
    List<com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO> obtenerReporteFinancieroTemporalMultiplesComplejos(
        @Param("inicio") java.time.LocalDate inicio,
        @Param("fin") java.time.LocalDate fin,
        @Param("complejoIds") List<Long> complejoIds);
}
