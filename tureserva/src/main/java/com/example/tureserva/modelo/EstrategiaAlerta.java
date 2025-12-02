package com.example.tureserva.modelo;

/**
 * Estrategia para determinar cuándo enviar alertas climáticas.
 */
public enum EstrategiaAlerta {
    /**
     * Alerta relativa: enviar X horas antes de la reserva.
     * Ejemplo: 24 horas antes del partido.
     */
    HORAS_ANTES,
    
    /**
     * Alerta absoluta: enviar a una hora fija del día de la reserva.
     * Ejemplo: Todos los días a las 06:00 AM para reservas de ese día.
     */
    HORARIO_FIJO
}
