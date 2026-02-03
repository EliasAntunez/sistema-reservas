package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Entidad que registra eventos de auditoría en el sistema.
 * Cada evento representa una acción significativa realizada por un usuario
 * sobre los recursos de un complejo deportivo.
 * 
 * ALCANCE: AdminComplejo solo puede ver eventos de sus propios complejos.
 */
@Entity
@Table(name = "auditoria_evento",
       indexes = {
           @Index(name = "idx_auditoria_complejo_fecha", columnList = "complejo_id, fecha_hora DESC"),
           @Index(name = "idx_auditoria_tipo_fecha", columnList = "tipo_evento, fecha_hora DESC"),
           @Index(name = "idx_auditoria_usuario", columnList = "usuario_email, fecha_hora DESC")
       })
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaEvento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Fecha y hora exacta en que ocurrió el evento.
     */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;
    
    /**
     * Tipo de evento auditado (CANCHA_CREADA, RESERVA_CANCELADA, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private TipoEvento tipoEvento;
    
    /**
     * Email del usuario que realizó la acción.
     */
    @Column(name = "usuario_email", nullable = false, length = 255)
    private String usuarioEmail;
    
    /**
     * Rol del usuario que realizó la acción (CLIENTE, ADMIN_COMPLEJO, SUPER_ADMIN).
     */
    @Column(name = "usuario_rol", nullable = false, length = 20)
    private String usuarioRol;
    
    /**
     * Nombre completo del usuario para facilitar la lectura (evita JOIN).
     * Formato: "Juan Pérez"
     */
    @Column(name = "usuario_nombre", length = 255)
    private String usuarioNombre;
    
    /**
     * ID del complejo al que pertenece el evento.
     * NULL solo para eventos globales (no aplica a AdminComplejo).
     */
    @Column(name = "complejo_id")
    private Long complejoId;
    
    /**
     * Nombre del complejo para facilitar la lectura (evita JOIN).
     */
    @Column(name = "complejo_nombre", length = 255)
    private String complejoNombre;
    
    /**
     * Descripción legible del evento.
     * Ejemplos: 
     * - "Cancha de Fútbol 5 creada"
     * - "Precio de Salón Principal modificado de $5000 a $6000"
     * - "Reserva RES-ABC123 cancelada por el administrador"
     */
    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;
    
    /**
     * Dirección IP desde la cual se realizó la acción.
     */
    @Column(name = "ip_address", length = 45) // IPv6 soporta hasta 45 caracteres
    private String ipAddress;
    
    /**
     * Datos del objeto ANTES de la modificación (formato JSON).
     * NULL para eventos de creación.
     * Ejemplo: {"nombre": "Cancha 1", "precio": "5000.00"}
     */
    @Column(name = "datos_anteriores", columnDefinition = "TEXT")
    private String datosAnteriores;
    
    /**
     * Datos del objeto DESPUÉS de la modificación (formato JSON).
     * NULL para eventos de eliminación.
     * Ejemplo: {"nombre": "Cancha 1 Renovada", "precio": "6000.00"}
     */
    @Column(name = "datos_nuevos", columnDefinition = "TEXT")
    private String datosNuevos;
    
    /**
     * ID del recurso afectado (cancha, salón, reserva, etc.).
     * Útil para rastrear la historia de un recurso específico.
     */
    @Column(name = "recurso_id")
    private Long recursoId;
    
    /**
     * Tipo de recurso afectado (CANCHA, SALON, RESERVA, etc.).
     */
    @Column(name = "recurso_tipo", length = 50)
    private String recursoTipo;
    
    /**
     * Constructor con parámetros principales.
     */
    public AuditoriaEvento(LocalDateTime fechaHora, TipoEvento tipoEvento, String usuarioEmail, 
                           String usuarioRol, String usuarioNombre, Long complejoId, 
                           String complejoNombre, String descripcion) {
        this.fechaHora = fechaHora;
        this.tipoEvento = tipoEvento;
        this.usuarioEmail = usuarioEmail;
        this.usuarioRol = usuarioRol;
        this.usuarioNombre = usuarioNombre;
        this.complejoId = complejoId;
        this.complejoNombre = complejoNombre;
        this.descripcion = descripcion;
    }
}
