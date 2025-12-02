package com.example.tureserva.servicio.clima;

import com.example.tureserva.modelo.ConfiguracionAlertaClima;
import com.example.tureserva.servicio.dto.TipoPrecipitacion;

import java.util.EnumMap;
import java.util.Map;

/**
 * Builder para construir el mapa de umbrales por tipo de precipitación.
 * Centraliza la lógica de fallback para evitar duplicación de código.
 */
public class UmbralesBuilder {
    
    private final Map<TipoPrecipitacion, Integer> umbrales = new EnumMap<>(TipoPrecipitacion.class);
    private int fallbackGeneral = AlertaClimaConstants.UMBRAL_PROBABILIDAD_DEFAULT;
    
    /**
     * Construye umbrales desde una configuración de alerta.
     * Si un umbral específico es null, usa el umbral general o el default.
     */
    public static Map<TipoPrecipitacion, Integer> fromConfiguracion(ConfiguracionAlertaClima config) {
        if (config == null) {
            return buildDefaults();
        }
        
        int fallback = config.getUmbralProbabilidad() != null 
            ? config.getUmbralProbabilidad() 
            : AlertaClimaConstants.UMBRAL_PROBABILIDAD_DEFAULT;
        
        return new UmbralesBuilder()
            .withFallback(fallback)
            .withLlovizna(config.getUmbralLlovizna())
            .withLluvia(config.getUmbralLluvia())
            .withChaparron(config.getUmbralChubascos())
            .withTormenta(config.getUmbralTormenta())
            .withNieve(config.getUmbralNieve())
            .build();
    }
    
    /**
     * Construye umbrales por defecto cuando no hay configuración.
     */
    public static Map<TipoPrecipitacion, Integer> buildDefaults() {
        return new UmbralesBuilder()
            .withFallback(AlertaClimaConstants.UMBRAL_PROBABILIDAD_DEFAULT)
            .build();
    }
    
    public UmbralesBuilder withFallback(int fallback) {
        this.fallbackGeneral = fallback;
        return this;
    }
    
    public UmbralesBuilder withLlovizna(Integer umbral) {
        umbrales.put(TipoPrecipitacion.LLOVIZNA, 
            umbral != null ? umbral : fallbackGeneral);
        return this;
    }
    
    public UmbralesBuilder withLluvia(Integer umbral) {
        umbrales.put(TipoPrecipitacion.LLUVIA, 
            umbral != null ? umbral : fallbackGeneral);
        return this;
    }
    
    public UmbralesBuilder withChaparron(Integer umbral) {
        umbrales.put(TipoPrecipitacion.CHAPARRON, 
            umbral != null ? umbral : fallbackGeneral);
        return this;
    }
    
    public UmbralesBuilder withTormenta(Integer umbral) {
        umbrales.put(TipoPrecipitacion.TORMENTA, 
            umbral != null ? umbral : fallbackGeneral);
        return this;
    }
    
    public UmbralesBuilder withNieve(Integer umbral) {
        umbrales.put(TipoPrecipitacion.NIEVE, 
            umbral != null ? umbral : fallbackGeneral);
        return this;
    }
    
    public Map<TipoPrecipitacion, Integer> build() {
        // Asegurar que todos los tipos tengan un umbral
        for (TipoPrecipitacion tipo : TipoPrecipitacion.values()) {
            umbrales.putIfAbsent(tipo, fallbackGeneral);
        }
        return new EnumMap<>(umbrales);
    }
}
