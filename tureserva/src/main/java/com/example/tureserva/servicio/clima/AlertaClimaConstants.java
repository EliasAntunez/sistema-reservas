package com.example.tureserva.servicio.clima;

/**
 * Constantes para el sistema de alertas climáticas.
 * Centraliza valores mágicos y configuraciones por defecto.
 */
public final class AlertaClimaConstants {
    
    private AlertaClimaConstants() {
        throw new UnsupportedOperationException("Clase de constantes no instanciable");
    }
    
    // Umbrales por defecto (%)
    public static final int UMBRAL_PROBABILIDAD_DEFAULT = 50;
    public static final int UMBRAL_LLOVIZNA_DEFAULT = 30;
    public static final int UMBRAL_LLUVIA_DEFAULT = 50;
    public static final int UMBRAL_CHAPARRON_DEFAULT = 60;
    public static final int UMBRAL_TORMENTA_DEFAULT = 70;
    public static final int UMBRAL_NIEVE_DEFAULT = 40;
    
    // Ventanas de tiempo (minutos)
    public static final int VENTANA_MINUTOS_ANTES = 30;
    public static final int VENTANA_MINUTOS_DESPUES = 30;
    
    // Mensajes de log
    public static final String LOG_ALERTA_ENVIADA = "✅ Alerta climática enviada: reserva={}, tipo={}, prob={}%";
    public static final String LOG_ERROR_ENVIO = "❌ Error enviando alerta para reserva {}: {}";
    public static final String LOG_UMBRAL_NO_SUPERADO = "⏭️ Umbral no superado: reserva={}, prob={}% < umbral={}% (tipo={})";
}
