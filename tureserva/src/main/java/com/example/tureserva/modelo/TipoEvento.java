package com.example.tureserva.modelo;

/**
 * Enumeración de tipos de eventos auditables en el sistema.
 * Cada evento representa una acción significativa realizada por un AdminComplejo
 * sobre los recursos de su(s) complejo(s).
 */
public enum TipoEvento {
    
    // ==================== CANCHAS ====================
    /**
     * Creación de una nueva cancha en el complejo.
     */
    CANCHA_CREADA("Cancha creada", "info"),
    
    /**
     * Modificación de datos de una cancha existente (nombre, superficie, características).
     */
    CANCHA_MODIFICADA("Cancha modificada", "warning"),
    
    /**
     * Eliminación de una cancha del sistema.
     */
    CANCHA_ELIMINADA("Cancha eliminada", "danger"),
    
    /**
     * Cambio de precio en una cancha.
     */
    CANCHA_PRECIO_MODIFICADO("Precio de cancha modificado", "warning"),
    
    // ==================== SALONES ====================
    /**
     * Creación de un nuevo salón en el complejo.
     */
    SALON_CREADO("Salón creado", "info"),
    
    /**
     * Modificación de datos de un salón existente.
     */
    SALON_MODIFICADO("Salón modificado", "warning"),
    
    /**
     * Eliminación de un salón del sistema.
     */
    SALON_ELIMINADO("Salón eliminado", "danger"),
    
    /**
     * Cambio de precio en un salón.
     */
    SALON_PRECIO_MODIFICADO("Precio de salón modificado", "warning"),
    
    // ==================== RESERVAS ====================
    /**
     * Creación de una nueva reserva por un cliente.
     */
    RESERVA_CREADA("Reserva creada", "info"),
    
    /**
     * Confirmación de una reserva (cambio de PENDIENTE a CONFIRMADA).
     */
    RESERVA_CONFIRMADA("Reserva confirmada", "success"),
    
    /**
     * Cancelación de una reserva por el administrador.
     */
    RESERVA_CANCELADA("Reserva cancelada", "danger"),
    
    /**
     * Reprogramación de una reserva existente (cambio de fecha/horario).
     */
    RESERVA_REPROGRAMADA("Reserva reprogramada", "warning"),
    
    /**
     * Modificación del estado de una reserva.
     */
    RESERVA_ESTADO_MODIFICADO("Estado de reserva modificado", "warning"),
    
    /**
     * Finalización de una reserva con registro de pago completo.
     */
    RESERVA_FINALIZADA("Reserva finalizada", "secondary"),
    
    // ==================== HORARIOS ====================
    /**
     * Modificación del horario master del complejo.
     */
    HORARIO_MASTER_MODIFICADO("Horario master modificado", "warning"),
    
    /**
     * Modificación del horario específico para canchas.
     */
    HORARIO_CANCHAS_MODIFICADO("Horario de canchas modificado", "warning"),
    
    /**
     * Modificación del horario específico para salones.
     */
    HORARIO_SALONES_MODIFICADO("Horario de salones modificado", "warning"),
    
    // ==================== POLÍTICAS ====================
    /**
     * Creación de una política de cancelación.
     */
    POLITICA_CANCELACION_CREADA("Política de cancelación creada", "info"),
    
    /**
     * Modificación de una política de cancelación existente.
     */
    POLITICA_CANCELACION_MODIFICADA("Política de cancelación modificada", "warning"),
    
    /**
     * Creación de una política de seña.
     */
    POLITICA_SENIA_CREADA("Política de seña creada", "info"),
    
    /**
     * Modificación de una política de seña existente.
     */
    POLITICA_SENIA_MODIFICADA("Política de seña modificada", "warning"),
    
    // ==================== SERVICIOS ADICIONALES ====================
    /**
     * Creación de un servicio adicional (parrilla, quincho, etc.).
     */
    SERVICIO_ADICIONAL_CREADO("Servicio adicional creado", "info"),
    
    /**
     * Modificación de un servicio adicional existente.
     */
    SERVICIO_ADICIONAL_MODIFICADO("Servicio adicional modificado", "warning"),
    
    /**
     * Eliminación de un servicio adicional.
     */
    SERVICIO_ADICIONAL_ELIMINADO("Servicio adicional eliminado", "danger"),
    
    // ==================== CONFIGURACIÓN ====================
    /**
     * Cambio en las credenciales de Mercado Pago (access token, public key).
     */
    CONFIGURACION_MP_MODIFICADA("Configuración de Mercado Pago modificada", "warning");
    
    private final String descripcion;
    private final String severidad; // info, success, warning, danger
    
    TipoEvento(String descripcion, String severidad) {
        this.descripcion = descripcion;
        this.severidad = severidad;
    }
    
    public String getDescripcion() {
        return descripcion;
    }
    
    public String getSeveridad() {
        return severidad;
    }
    
    /**
     * Obtiene la clase CSS de Bootstrap correspondiente a la severidad.
     * @return clase CSS (text-info, text-success, text-warning, text-danger)
     */
    public String getClaseCss() {
        return "text-" + severidad;
    }
    
    /**
     * Obtiene la clase de badge de Bootstrap correspondiente a la severidad.
     * @return clase CSS (badge bg-info, bg-success, etc.)
     */
    public String getBadgeClass() {
        return "badge bg-" + severidad;
    }
}
