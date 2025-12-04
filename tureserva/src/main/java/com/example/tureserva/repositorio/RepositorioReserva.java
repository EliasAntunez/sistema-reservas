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
     * Encuentra reservas CONFIRMADA cuyo horario ya pasó y no tienen pago completo registrado.
     * Se asume que el pago completo se registra con TipoPago.PAGO_COMPLETO.
     * Este método debe ser ajustado si la lógica de pagos cambia.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.detalles d " +
           "WHERE r.estado = :estado " +
           "AND (SELECT MAX(d2.horaFin) FROM DetalleReserva d2 WHERE d2.reserva = r) IS NOT NULL " +
           "AND (r.fechaReserva < :fechaActual OR (r.fechaReserva = :fechaActual AND (SELECT MAX(d2.horaFin) FROM DetalleReserva d2 WHERE d2.reserva = r) < :horaActual)) " +
           "AND NOT EXISTS (SELECT 1 FROM Pago p WHERE p.reserva = r AND p.tipoPago = 'PAGO_COMPLETO')")
    List<Reserva> findReservasVencidasSinPago(@Param("fechaActual") java.time.LocalDate fechaActual,
                                              @Param("horaActual") java.time.LocalTime horaActual,
                                              @Param("estado") EstadoReserva estado);
    
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
     * Encuentra una reserva con sus detalles, servicios adicionales y espacios cargados.
     * Útil para vistas que necesitan listar los servicios asociados a cada detalle.
     */
    @Query("SELECT DISTINCT r FROM Reserva r " +
           "LEFT JOIN FETCH r.cliente " +
           "LEFT JOIN FETCH r.detalles d " +
           "LEFT JOIN FETCH d.serviciosAdicionales s " +
           "LEFT JOIN FETCH s.servicioAdicional sa " +
           "LEFT JOIN FETCH d.espacioReservable e " +
           "LEFT JOIN FETCH e.complejoDeportivo c " +
           "WHERE r.id = :id")
    Optional<Reserva> findByIdWithDetallesAndServicios(@Param("id") Long id);
    
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
        * Obtiene un reporte financiero agregando por espacio (nombre y tipo),
        * con la cantidad de reservas e ingresos (suma de subtotales) dentro
        * de un rango de fechas y opcionalmente por complejo.
        */
       @Query("SELECT new com.example.tureserva.servicio.dto.ReporteFinancieroDTO(" +
                 "e.nombre, " +
                 "CASE WHEN TYPE(e) = com.example.tureserva.modelo.Cancha THEN 'CANCHA' " +
                 "     WHEN TYPE(e) = com.example.tureserva.modelo.Salon THEN 'SALON' " +
                 "     ELSE 'N/A' END, " +
                 "COUNT(DISTINCT r.id), COALESCE(SUM(d.subtotal),0) ) " +
                 "FROM Reserva r " +
                 "JOIN r.detalles d " +
                 "JOIN d.espacioReservable e " +
                 "JOIN e.complejoDeportivo c " +
                 "WHERE r.fechaReserva BETWEEN :inicio AND :fin " +
                 "AND (:complejoId IS NULL OR c.id = :complejoId) " +
                 "GROUP BY e.nombre, CASE WHEN TYPE(e) = com.example.tureserva.modelo.Cancha THEN 'CANCHA' " +
                 "                      WHEN TYPE(e) = com.example.tureserva.modelo.Salon THEN 'SALON' " +
                 "                      ELSE 'N/A' END " +
                 "ORDER BY SUM(d.subtotal) DESC")
       List<com.example.tureserva.servicio.dto.ReporteFinancieroDTO> obtenerReporteFinanciero(
              @Param("inicio") java.time.LocalDate inicio,
              @Param("fin") java.time.LocalDate fin,
              @Param("complejoId") Long complejoId);

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

       // ==================== CONSULTAS PARA ALERTAS CLIMÁTICAS ====================
       
       /**
        * Busca reservas para estrategia HORAS_ANTES.
        * Encuentra reservas confirmadas de un complejo en una fecha y rango horario específicos
        * que aún no tienen alerta enviada.
        */
       @Query("SELECT DISTINCT r FROM Reserva r " +
              "LEFT JOIN FETCH r.cliente " +
              "LEFT JOIN FETCH r.detalles d " +
              "LEFT JOIN FETCH d.espacioReservable e " +
              "LEFT JOIN FETCH e.complejoDeportivo c " +
              "WHERE c.id = :complejoId " +
              "AND r.fechaReserva = :fecha " +
              "AND d.horaInicio >= :horaInicio " +
              "AND d.horaInicio <= :horaFin " +
              "AND r.estado = :estado " +
              "AND r.alertaEnviada = :alertaEnviada")
       List<Reserva> findReservasParaAlerta(
           @Param("complejoId") Long complejoId,
           @Param("fecha") LocalDate fecha,
           @Param("horaInicio") java.time.LocalTime horaInicio,
           @Param("horaFin") java.time.LocalTime horaFin,
           @Param("estado") EstadoReserva estado,
           @Param("alertaEnviada") Boolean alertaEnviada
       );
       
       /**
        * Busca reservas para estrategia HORARIO_FIJO.
        * Encuentra todas las reservas confirmadas de un complejo en una fecha específica
        * que aún no tienen alerta enviada.
        */
       @Query("SELECT DISTINCT r FROM Reserva r " +
              "LEFT JOIN FETCH r.cliente " +
              "LEFT JOIN FETCH r.detalles d " +
              "LEFT JOIN FETCH d.espacioReservable e " +
              "LEFT JOIN FETCH e.complejoDeportivo c " +
              "WHERE c.id = :complejoId " +
              "AND r.fechaReserva = :fecha " +
              "AND r.estado = :estado " +
              "AND r.alertaEnviada = :alertaEnviada")
       List<Reserva> findReservasPorComplejoFechaEstadoYAlerta(
           @Param("complejoId") Long complejoId,
           @Param("fecha") LocalDate fecha,
           @Param("estado") EstadoReserva estado,
           @Param("alertaEnviada") Boolean alertaEnviada
       );

       // ==================== CONSULTAS PARA RECUPERO DE SEÑAS ====================
       
       /**
        * Encuentra reservas CONFIRMADA cuyo plazo de cancelación gratuita ha vencido
        * pero aún no se les ha enviado el aviso de recupero mediante Oferta Flash.
        * 
        * Lógica: busca reservas donde:
        * 1. Estado = CONFIRMADA
        * 2. No se ha enviado aviso de recupero (avisoRecuperoEnviado = false o null)
        * 3. El tiempo restante hasta el inicio es <= horasAnticipacionMinima de la política
        * 4. Aún no ha comenzado la reserva (hora_inicio > hora actual)
        */
       @Query("SELECT DISTINCT r FROM Reserva r " +
              "LEFT JOIN FETCH r.cliente " +
              "LEFT JOIN FETCH r.detalles d " +
              "LEFT JOIN FETCH d.espacioReservable e " +
              "LEFT JOIN FETCH e.politicaCancelacion pc " +
              "LEFT JOIN FETCH e.complejoDeportivo " +
              "WHERE r.estado = 'CONFIRMADA' " +
              "AND (r.avisoRecuperoEnviado = false OR r.avisoRecuperoEnviado IS NULL) " +
              "AND pc IS NOT NULL " +
              "AND (" +
              "  (r.fechaReserva = CURRENT_DATE AND d.horaInicio > CURRENT_TIME) OR " +
              "  r.fechaReserva > CURRENT_DATE" +
              ") " +
              "ORDER BY r.fechaReserva ASC, d.horaInicio ASC")
       List<Reserva> findReservasParaAvisoRecupero();

       /**
        * Consulta nativa para obtener la ocupación horaria (cantidad de detalles de reserva)
        * agrupada por día ISO (1=Lunes .. 7=Domingo) y hora (0..23).
        */
                      @Query(value = """
                                                  WITH details AS (
                                                         SELECT d.id AS detalle_id,
                                                                                    COALESCE(d.fecha_reserva, r.fecha_reserva) AS fecha_base,
                                                                                    (COALESCE(d.fecha_reserva, r.fecha_reserva) + d.hora_inicio)::timestamp AS start_ts,
                                                                                    (COALESCE(d.fecha_reserva, r.fecha_reserva) + (CASE WHEN d.hora_fin <= d.hora_inicio THEN d.hora_fin + interval '24 hours' ELSE d.hora_fin END))::timestamp AS end_ts
                                                         FROM detalle_reserva d
                                                         JOIN reserva r ON d.reserva_id = r.id
                                                         JOIN espacio_reservable e ON d.espacio_reservable_id = e.id_espacio_reservable
                                                         JOIN complejo_deportivo c ON e.complejo_id = c.id_complejo
                                                         WHERE COALESCE(d.fecha_reserva, r.fecha_reserva) BETWEEN :inicio AND :fin
                                                                AND (:complejoId IS NULL OR c.id_complejo = :complejoId)
                                                                AND r.estado IN ('CONFIRMADA','FINALIZADA')
                                                  )
                                                  SELECT EXTRACT(ISODOW FROM slot) AS day_of_week,
                                                                             EXTRACT(HOUR FROM slot) AS hour,
                                                                             COUNT(DISTINCT detalle_id) AS count
                                                  FROM (
                                                         SELECT detalle_id, (start_ts + (g * interval '1 hour')) AS slot
                                                         FROM details
                                                         JOIN LATERAL (
                                                                SELECT generate_series(0, GREATEST(0, (floor(EXTRACT(EPOCH FROM (end_ts - start_ts))/3600)::int - 1))) AS g
                                                         ) gen ON true
                                                  ) s
                                                  GROUP BY day_of_week, hour
                                                  ORDER BY day_of_week, hour
                                                  """, nativeQuery = true)
       List<OcupacionHorariaDTO> obtenerOcupacionHoraria(
                     @Param("complejoId") Long complejoId,
                     @Param("inicio") java.time.LocalDate inicio,
                     @Param("fin") java.time.LocalDate fin);
}
