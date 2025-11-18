package com.example.tureserva.modelo.enums;

/**
 * Métodos de pago disponibles en el sistema.
 * 
 * @author TuReserva
 */
public enum MetodoPago {
    /**
     * Pago mediante Mercado Pago (online)
     */
    MERCADO_PAGO("Mercado Pago", true),
    
    /**
     * Pago en efectivo
     */
    EFECTIVO("Efectivo", false),
    
    /**
     * Pago con tarjeta de débito
     */
    TARJETA_DEBITO("Tarjeta de Débito", false),
    
    /**
     * Pago con tarjeta de crédito
     */
    TARJETA_CREDITO("Tarjeta de Crédito", false),
    
    /**
     * Transferencia bancaria
     */
    TRANSFERENCIA("Transferencia Bancaria", false);

    private final String displayName;
    private final boolean esOnline;

    MetodoPago(String displayName, boolean esOnline) {
        this.displayName = displayName;
        this.esOnline = esOnline;
    }

    /**
     * Nombre para mostrar en UI
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Indica si el método de pago es online (requiere integración)
     */
    public boolean isOnline() {
        return esOnline;
    }
}
