package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.AuditoriaEvento;
import com.example.tureserva.modelo.TipoEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para operaciones de auditoría.
 * Proporciona consultas especializadas para filtrar eventos por diferentes criterios.
 */
@Repository
public interface RepositorioAuditoriaEvento extends JpaRepository<AuditoriaEvento, Long> {
    
    /**
     * Obtiene todos los eventos de un complejo específico, ordenados por fecha descendente.
     * CRÍTICO: AdminComplejo solo debe usar esta consulta con su propio complejoId.
     * 
     * @param complejoId ID del complejo
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    Page<AuditoriaEvento> findByComplejoIdOrderByFechaHoraDesc(Long complejoId, Pageable pageable);
    
    /**
     * Obtiene eventos de un complejo filtrados por tipo de evento.
     * 
     * @param complejoId ID del complejo
     * @param tipoEvento tipo de evento a filtrar
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    Page<AuditoriaEvento> findByComplejoIdAndTipoEventoOrderByFechaHoraDesc(
            Long complejoId, TipoEvento tipoEvento, Pageable pageable);
    
    /**
     * Obtiene eventos de un complejo en un rango de fechas.
     * 
     * @param complejoId ID del complejo
     * @param desde fecha y hora inicial (inclusive)
     * @param hasta fecha y hora final (inclusive)
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    Page<AuditoriaEvento> findByComplejoIdAndFechaHoraBetweenOrderByFechaHoraDesc(
            Long complejoId, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);
    
    /**
     * Obtiene eventos de un complejo filtrados por usuario.
     * 
     * @param complejoId ID del complejo
     * @param usuarioEmail email del usuario
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    Page<AuditoriaEvento> findByComplejoIdAndUsuarioEmailOrderByFechaHoraDesc(
            Long complejoId, String usuarioEmail, Pageable pageable);
    
    /**
     * Obtiene el historial completo de un recurso específico (cancha, salón, reserva, etc.).
     * 
     * @param complejoId ID del complejo
     * @param recursoTipo tipo de recurso (CANCHA, SALON, RESERVA, etc.)
     * @param recursoId ID del recurso
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    Page<AuditoriaEvento> findByComplejoIdAndRecursoTipoAndRecursoIdOrderByFechaHoraDesc(
            Long complejoId, String recursoTipo, Long recursoId, Pageable pageable);
    
    /**
     * Búsqueda general por texto en la descripción.
     * 
     * @param complejoId ID del complejo
     * @param texto texto a buscar en la descripción
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    @Query("SELECT a FROM AuditoriaEvento a WHERE a.complejoId = :complejoId " +
           "AND LOWER(a.descripcion) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "ORDER BY a.fechaHora DESC")
    Page<AuditoriaEvento> buscarPorTexto(@Param("complejoId") Long complejoId, 
                                          @Param("texto") String texto, 
                                          Pageable pageable);
    
    /**
     * Búsqueda avanzada con múltiples filtros opcionales.
     * 
     * @param complejoId ID del complejo (obligatorio)
     * @param tipoEvento tipo de evento (opcional)
     * @param desde fecha inicial (opcional)
     * @param hasta fecha final (opcional)
     * @param usuarioEmail email del usuario (opcional)
     * @param texto búsqueda en descripción (opcional)
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    @Query(value = "SELECT * FROM auditoria_evento a WHERE a.complejo_id = :complejoId " +
           "AND (COALESCE(:tipoEvento, '') = '' OR a.tipo_evento = :tipoEvento) " +
           "AND a.fecha_hora >= COALESCE(:desde, '1970-01-01 00:00:00'::timestamp) " +
           "AND a.fecha_hora <= COALESCE(:hasta, '2099-12-31 23:59:59'::timestamp) " +
           "AND (COALESCE(:usuarioEmail, '') = '' OR a.usuario_email = :usuarioEmail) " +
           "AND (COALESCE(:texto, '') = '' OR LOWER(a.descripcion) LIKE LOWER(CONCAT('%', :texto, '%'))) " +
           "ORDER BY a.fecha_hora DESC",
           nativeQuery = true)
    Page<AuditoriaEvento> buscarConFiltros(
            @Param("complejoId") Long complejoId,
            @Param("tipoEvento") String tipoEvento,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("usuarioEmail") String usuarioEmail,
            @Param("texto") String texto,
            Pageable pageable);
    
    /**
     * Obtiene eventos de múltiples complejos (para AdminComplejo con varios complejos).
     * 
     * @param complejosIds lista de IDs de complejos
     * @param pageable configuración de paginación
     * @return página de eventos
     */
    Page<AuditoriaEvento> findByComplejoIdInOrderByFechaHoraDesc(List<Long> complejosIds, Pageable pageable);
    
    /**
     * Cuenta eventos por tipo para un complejo (útil para dashboards).
     * 
     * @param complejoId ID del complejo
     * @param tipoEvento tipo de evento
     * @return cantidad de eventos
     */
    long countByComplejoIdAndTipoEvento(Long complejoId, TipoEvento tipoEvento);
    
    /**
     * Elimina eventos antiguos (para política de retención de datos).
     * Se ejecutará mediante un job programado.
     * 
     * @param fechaLimite eventos anteriores a esta fecha serán eliminados
     * @return cantidad de registros eliminados
     */
    long deleteByFechaHoraBefore(LocalDateTime fechaLimite);
}
