package com.example.tureserva.servicio.dto;

/**
 * Tipos simplificados de precipitación derivados del código WMO.
 * Nomenclatura adaptada para Argentina.
 * Incluye etiquetas legibles y umbrales sugeridos por defecto.
 */
public enum TipoPrecipitacion {
    LLOVIZNA("Llovizna", 30),
    LLUVIA("Lluvia", 50),
    CHAPARRON("Chaparrón", 60),  // Nombre común en Argentina
    TORMENTA("Tormenta", 70),
    NIEVE("Nieve", 40);
    
    private final String etiqueta;
    private final int umbralDefaultSugerido;
    
    TipoPrecipitacion(String etiqueta, int umbralDefaultSugerido) {
        this.etiqueta = etiqueta;
        this.umbralDefaultSugerido = umbralDefaultSugerido;
    }
    
    /**
     * Obtiene la etiqueta legible para mostrar en templates y emails.
     */
    public String getEtiqueta() {
        return etiqueta;
    }
    
    /**
     * Obtiene el umbral por defecto sugerido para este tipo (solo informativo).
     */
    public int getUmbralDefaultSugerido() {
        return umbralDefaultSugerido;
    }
}
