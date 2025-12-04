package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.OfertaFlash;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.EstadoOferta;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioOfertaFlash extends JpaRepository<OfertaFlash, Long> {
    
    /**
     * Busca una oferta por su token único con todos los datos cargados
     */
    @Query("SELECT DISTINCT o FROM OfertaFlash o " +
           "LEFT JOIN FETCH o.reservaOriginal r " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo " +
           "LEFT JOIN FETCH o.clienteOriginal " +
           "WHERE o.token = :token")
    Optional<OfertaFlash> findByTokenWithDetalles(@Param("token") String token);
    
    /**
     * Busca una oferta por su token (sin joins)
     */
    Optional<OfertaFlash> findByToken(String token);
    
    /**
     * Busca una oferta por su token con PESSIMISTIC_WRITE lock.
     * Este método DEBE usarse en reclamarOferta() para prevenir race conditions.
     * 
     * El lock se mantiene hasta que la transacción se complete (commit/rollback).
     * Si otra transacción ya tiene el lock, esta espera hasta 3 segundos (timeout).
     * 
     * @param token Token único de la oferta
     * @return Oferta si existe, vacío si no
     * @throws jakarta.persistence.LockTimeoutException si no puede adquirir el lock en 3s
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT o FROM OfertaFlash o WHERE o.token = :token")
    Optional<OfertaFlash> findByTokenWithLock(@Param("token") String token);
    
    /**
     * Encuentra todas las ofertas disponibles que no han expirado
     */
    @Query("SELECT o FROM OfertaFlash o " +
           "WHERE o.estado = :estado " +
           "AND o.fechaExpiracion > :now " +
           "ORDER BY o.fechaCreacion DESC")
    List<OfertaFlash> findOfertasDisponibles(
        @Param("estado") EstadoOferta estado,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Encuentra ofertas expiradas que aún están en estado DISPONIBLE
     * (para procesamiento batch de expiración)
     */
    @Query("SELECT o FROM OfertaFlash o " +
           "WHERE o.estado = 'DISPONIBLE' " +
           "AND o.fechaExpiracion <= :now")
    List<OfertaFlash> findOfertasExpiradas(@Param("now") LocalDateTime now);
    
    /**
     * Encuentra todas las ofertas de un cliente original (que canceló)
     */
    List<OfertaFlash> findByClienteOriginalOrderByFechaCreacionDesc(Cliente cliente);
    
    /**
     * Encuentra todas las ofertas reclamadas por un cliente
     */
    List<OfertaFlash> findByClienteReclamanteOrderByFechaReclamacionDesc(Cliente cliente);
    
    /**
     * Encuentra la oferta asociada a una reserva original
     */
    Optional<OfertaFlash> findByReservaOriginal(Reserva reserva);
    
    /**
     * Encuentra la oferta asociada a una reserva nueva (reclamada)
     */
    Optional<OfertaFlash> findByReservaNueva(Reserva reserva);
    
    /**
     * Cuenta ofertas activas (disponibles y no expiradas)
     */
    @Query("SELECT COUNT(o) FROM OfertaFlash o " +
           "WHERE o.estado = 'DISPONIBLE' " +
           "AND o.fechaExpiracion > :now")
    long countOfertasActivas(@Param("now") LocalDateTime now);
    
    /**
     * Encuentra ofertas disponibles de un complejo específico
     * (útil para enviar notificaciones a clientes del mismo complejo)
     */
    @Query("SELECT DISTINCT o FROM OfertaFlash o " +
           "JOIN o.reservaOriginal r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id_complejo = :complejoId " +
           "AND o.estado = 'DISPONIBLE' " +
           "AND o.fechaExpiracion > :now " +
           "ORDER BY o.fechaCreacion DESC")
    List<OfertaFlash> findOfertasDisponiblesPorComplejo(
        @Param("complejoId") Long complejoId,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Encuentra clientes candidatos para notificar sobre una oferta flash.
     * 
     * <p><b>Criterios de selección:</b>
     * <ul>
     *   <li>Cliente con reservas confirmadas o finalizadas en los últimos 6 meses</li>
     *   <li>Reservas del mismo complejo deportivo que la oferta</li>
     *   <li>Excluye al cliente original de la oferta</li>
     * </ul>
     * 
     * <p><b>Performance:</b> Query directa SQL en vez de findAll() + stream().
     * Evita cargar toda la tabla de reservas en memoria.
     * 
     * @param complejoId ID del complejo deportivo de la oferta
     * @param clienteOriginalId ID del cliente que canceló (excluir)
     * @param fechaLimite Fecha mínima de reservas (ej: 6 meses atrás)
     * @return Lista de clientes únicos que cumplen los criterios
     */
    @Query("SELECT DISTINCT r.cliente FROM Reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id_complejo = :complejoId " +
           "AND r.cliente.id <> :clienteOriginalId " +
           "AND r.fechaCreacion >= :fechaLimite " +
           "AND (r.estado = 'CONFIRMADA' OR r.estado = 'FINALIZADA')")
    List<Cliente> findClientesCandidatosParaOferta(
        @Param("complejoId") Long complejoId,
        @Param("clienteOriginalId") Long clienteOriginalId,
        @Param("fechaLimite") LocalDateTime fechaLimite
    );
}
