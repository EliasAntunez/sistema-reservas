package com.example.tureserva.modelo.enums;

/**
 * Estados posibles de una reserva.
 * 
 * <p>Flujo típico:
 * PENDIENTE → CONFIRMADA (cuando se paga la seña)
 * PENDIENTE → CANCELADA (si se cancela antes de confirmar)
 * CONFIRMADA → CANCELADA (si se cancela después de confirmar)
 * 
 * @author TuReserva
 */
public enum EstadoReserva {
    /**
     * Reserva creada pero aún no confirmada (sin pago de seña)
     */
    PENDIENTE("Pendiente", "warning"),
    
    /**
     * Reserva confirmada (seña pagada o confirmación del administrador)
     */
    CONFIRMADA("Confirmada", "success"),
    
    /**
     * Reserva cancelada (por el cliente o por el administrador)
     */
    CANCELADA("Cancelada", "danger"),

    /**
     * Reserva finalizada
     */
    FINALIZADA("Finalizada", "secondary");

    private final String displayName;
    private final String badgeClass;

    EstadoReserva(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    /**
     * Nombre para mostrar en UI
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Clase CSS de Bootstrap para badges
     * @return "success", "warning" o "danger"
     */
    public String getBadgeClass() {
        return badgeClass;
    }
}
