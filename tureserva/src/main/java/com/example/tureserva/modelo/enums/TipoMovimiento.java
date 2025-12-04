package com.example.tureserva.modelo.enums;

/**
 * Tipos de movimiento en la cuenta corriente.
 * 
 * @author TuReserva
 */
public enum TipoMovimiento {
    /**
     * Acreditación (ingreso de créditos)
     */
    CREDITO("Crédito", "success"),
    
    /**
     * Débito (uso de créditos)
     */
    DEBITO("Débito", "danger"),
    
    /**
     * Acreditación por recupero de seña (Oferta Flash vendida)
     */
    RECUPERO_SENIA("Recupero de Seña", "info"),
    
    /**
     * Débito por uso de créditos en una reserva
     */
    USO_CREDITO_RESERVA("Uso en Reserva", "warning");

    private final String displayName;
    private final String badgeClass;

    TipoMovimiento(String displayName, String badgeClass) {
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
     */
    public String getBadgeClass() {
        return badgeClass;
    }
}
