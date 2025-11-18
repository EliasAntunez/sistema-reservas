package com.example.tureserva.modelo.enums;

/**
 * Tipos de cobro para servicios adicionales.
 * - POR_HORA: se cobra por hora (no suele admitir cantidad adicional por unidad)
 * - POR_RESERVA: tarifa única por reserva (no admite cantidad)
 * - FIJO: monto fijo (no admite cantidad)
 * - POR_UNIDAD: se cobra por unidad (ej: alquiler de pelotas) — admite cantidad
 * - POR_PERSONA: se cobra por persona — admite cantidad
 */
public enum TipoDeCobro {
    POR_HORA,
    POR_RESERVA,
    FIJO,
    POR_UNIDAD,
    POR_PERSONA;

    /**
     * Indica si el tipo de cobro admite especificar una cantidad por servicio.
     */
    public boolean permiteCantidad() {
        return this == POR_UNIDAD || this == POR_PERSONA;
    }
}
