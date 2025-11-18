package com.example.tureserva.modelo.enums;

/**
 * Tipo de pago en el sistema de reservas.
 * 
 * <p>Flujo típico:
 * - Si NO requiere seña: Un solo pago de tipo PAGO_COMPLETO
 * - Si requiere seña: Dos pagos (SENIA + PAGO_COMPLETO)
 * 
 * @author TuReserva
 */
public enum TipoPago {
    /**
     * Pago de seña/anticipo (generalmente por Mercado Pago u otro medio online)
     */
    SENIA("Seña"),
    
    /**
     * Pago completo/final (registrado por admin/empleado)
     */
    PAGO_COMPLETO("Pago Completo");

    private final String displayName;

    TipoPago(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
