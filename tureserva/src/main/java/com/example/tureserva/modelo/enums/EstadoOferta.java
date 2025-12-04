package com.example.tureserva.modelo.enums;

/**
 * Estados posibles de una Oferta Flash.
 * 
 * <p>Flujo típico:
 * DISPONIBLE → RECLAMADA (cuando un cliente la toma)
 * DISPONIBLE → EXPIRADA (cuando pasa el tiempo límite)
 * DISPONIBLE → CANCELADA (cuando el cliente original la cancela)
 * 
 * @author TuReserva
 */
public enum EstadoOferta {
    /**
     * Oferta disponible para ser reclamada
     */
    DISPONIBLE("Disponible", "success"),
    
    /**
     * Oferta reclamada por un cliente
     */
    RECLAMADA("Reclamada", "info"),
    
    /**
     * Oferta expirada (venció el tiempo límite)
     */
    EXPIRADA("Expirada", "warning"),
    
    /**
     * Oferta cancelada por el cliente original
     */
    CANCELADA("Cancelada", "danger");

    private final String displayName;
    private final String badgeClass;

    EstadoOferta(String displayName, String badgeClass) {
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
     * @return "success", "info", "warning" o "danger"
     */
    public String getBadgeClass() {
        return badgeClass;
    }
}
