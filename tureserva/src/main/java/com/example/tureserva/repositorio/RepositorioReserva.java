package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioReserva extends JpaRepository<Reserva, Long> {
    
    /**
     * Encuentra todas las reservas de un cliente
     */
    List<Reserva> findByCliente(Cliente cliente);
    
    /**
     * Encuentra todas las reservas de un cliente ordenadas por fecha descendente
     */
    List<Reserva> findByClienteOrderByFechaReservaDesc(Cliente cliente);
    
    /**
     * Encuentra todas las reservas de un cliente con un estado específico
     */
    List<Reserva> findByClienteAndEstado(Cliente cliente, EstadoReserva estado);
    
    /**
     * Encuentra todas las reservas para una fecha específica
     */
    List<Reserva> findByFechaReserva(LocalDate fecha);
    
    /**
     * Encuentra todas las reservas de un cliente para una fecha específica
     */
    List<Reserva> findByClienteAndFechaReserva(Cliente cliente, LocalDate fecha);
    
    /**
     * Encuentra una reserva con sus detalles y espacios cargados (evita LazyInitializationException)
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE r.id = :id")
    Optional<Reserva> findByIdWithDetalles(@Param("id") Long id);
    
    /**
     * Encuentra todas las reservas de un cliente con detalles y espacios cargados
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo " +
           "WHERE r.cliente = :cliente " +
           "ORDER BY r.fechaReserva DESC")
    List<Reserva> findByClienteWithDetalles(@Param("cliente") Cliente cliente);
    
    /**
     * Encuentra todas las reservas de un cliente filtradas por estado con detalles cargados
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo " +
           "WHERE r.cliente = :cliente AND r.estado = :estado " +
           "ORDER BY r.fechaReserva DESC")
    List<Reserva> findByClienteAndEstadoWithDetalles(@Param("cliente") Cliente cliente, 
                                                       @Param("estado") EstadoReserva estado);
    
    /**
     * Cuenta las reservas de un cliente
     */
    long countByCliente(Cliente cliente);
    
    /**
     * Cuenta las reservas confirmadas de un cliente
     */
    long countByClienteAndEstado(Cliente cliente, EstadoReserva estado);
    
    // ==================== CONSULTAS PARA ADMINISTRADOR DE COMPLEJO ====================
    
    /**
     * Obtiene todas las reservas de un complejo deportivo con sus detalles cargados.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE c.id = :complejoId " +
           "ORDER BY r.fechaReserva DESC, r.id DESC")
    List<Reserva> findByComplejoDeportivoId(@Param("complejoId") Long complejoId);
    
    /**
     * Obtiene las reservas de un complejo filtradas por fecha.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE c.id = :complejoId AND r.fechaReserva = :fecha " +
           "ORDER BY r.id DESC")
    List<Reserva> findByComplejoDeportivoIdAndFecha(
        @Param("complejoId") Long complejoId,
        @Param("fecha") LocalDate fecha
    );
    
    /**
     * Obtiene las reservas de un complejo filtradas por estado.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE c.id = :complejoId AND r.estado = :estado " +
           "ORDER BY r.fechaReserva DESC, r.id DESC")
    List<Reserva> findByComplejoDeportivoIdAndEstado(
        @Param("complejoId") Long complejoId,
        @Param("estado") EstadoReserva estado
    );
    
    /**
     * Obtiene las reservas de un complejo filtradas por fecha y estado.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE c.id = :complejoId AND r.fechaReserva = :fecha AND r.estado = :estado " +
           "ORDER BY r.id DESC")
    List<Reserva> findByComplejoDeportivoIdAndFechaAndEstado(
        @Param("complejoId") Long complejoId,
        @Param("fecha") LocalDate fecha,
        @Param("estado") EstadoReserva estado
    );
    
    /**
     * Cuenta las reservas de un complejo por estado.
     */
    @Query("SELECT COUNT(r) FROM Reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id = :complejoId AND r.estado = :estado")
    long countByComplejoDeportivoIdAndEstado(
        @Param("complejoId") Long complejoId,
        @Param("estado") EstadoReserva estado
    );
    
    // ==================== CONSULTAS CON PAGINACIÓN ====================
    
    /**
     * Obtiene los IDs de reservas de un complejo con paginación (sin JOIN FETCH).
     */
    @Query("SELECT DISTINCT r.id FROM Reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id = :complejoId")
    Page<Long> findIdsByComplejoDeportivoId(
        @Param("complejoId") Long complejoId,
        Pageable pageable
    );
    
    /**
     * Obtiene los IDs de reservas filtradas por fecha con paginación.
     */
    @Query("SELECT DISTINCT r.id FROM Reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id = :complejoId AND r.fechaReserva = :fecha")
    Page<Long> findIdsByComplejoDeportivoIdAndFecha(
        @Param("complejoId") Long complejoId,
        @Param("fecha") LocalDate fecha,
        Pageable pageable
    );
    
    /**
     * Obtiene los IDs de reservas filtradas por estado con paginación.
     */
    @Query("SELECT DISTINCT r.id FROM Reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id = :complejoId AND r.estado = :estado")
    Page<Long> findIdsByComplejoDeportivoIdAndEstado(
        @Param("complejoId") Long complejoId,
        @Param("estado") EstadoReserva estado,
        Pageable pageable
    );
    
    /**
     * Obtiene los IDs de reservas filtradas por fecha y estado con paginación.
     */
    @Query("SELECT DISTINCT r.id FROM Reserva r " +
           "JOIN r.detalles d " +
           "JOIN d.espacioReservable e " +
           "WHERE e.complejoDeportivo.id = :complejoId AND r.fechaReserva = :fecha AND r.estado = :estado")
    Page<Long> findIdsByComplejoDeportivoIdAndFechaAndEstado(
        @Param("complejoId") Long complejoId,
        @Param("fecha") LocalDate fecha,
        @Param("estado") EstadoReserva estado,
        Pageable pageable
    );
    
    /**
     * Carga reservas completas por IDs con todos los datos necesarios.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo " +
           "WHERE r.id IN :ids " +
           "ORDER BY r.fechaReserva DESC, r.id DESC")
    List<Reserva> findByIdInWithDetalles(@Param("ids") List<Long> ids);

    /**
     * Encuentra una reserva por su código único con todos los detalles cargados.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE r.codigoReserva = :codigo")
    Optional<Reserva> findByCodigoReservaWithDetalles(@Param("codigo") String codigo);

    /**
     * Encuentra una reserva por su código único (sin detalles).
     */
    Optional<Reserva> findByCodigoReserva(String codigoReserva);

       /**
        * Obtiene los IDs de reservas de un cliente con paginación (sin JOIN FETCH).
        */
       @Query("SELECT DISTINCT r.id FROM Reserva r WHERE r.cliente = :cliente")
       Page<Long> findIdsByCliente(@Param("cliente") Cliente cliente, Pageable pageable);

       /**
        * Obtiene los IDs de reservas de un cliente filtradas por estado con paginación.
        */
       @Query("SELECT DISTINCT r.id FROM Reserva r WHERE r.cliente = :cliente AND r.estado = :estado")
       Page<Long> findIdsByClienteAndEstado(@Param("cliente") Cliente cliente, @Param("estado") EstadoReserva estado, Pageable pageable);

       Boolean existsByCodigoReserva(String codigoReserva);
}
