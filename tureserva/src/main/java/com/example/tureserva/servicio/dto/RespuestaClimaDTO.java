package com.example.tureserva.servicio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Respuesta del servicio de clima con información relevante para alertas.
 */
@Getter
@Setter
@AllArgsConstructor
public class RespuestaClimaDTO {
    
    /**
     * Indica si hay condiciones climáticas adversas (lluvia/tormenta).
     */
    private boolean hayMalClima;
    
    /**
     * Probabilidad de precipitación (0-100%).
     */
    private Integer probabilidadPrecipitacion;
    
    /**
     * Código WMO del clima (para más detalles).
     * Códigos relevantes:
     * - 51, 53, 55: Llovizna
     * - 61, 63, 65: Lluvia
     * - 71, 73, 75: Nieve
     * - 80, 81, 82: Chubascos
     * - 95, 96, 99: Tormentas
     */
    private Integer codigoClima;
    
    /**
     * Descripción legible del clima.
     */
    private String descripcion;
    
    /**
     * Tipo simplificado de precipitación derivado del código WMO.
     */
    private com.example.tureserva.servicio.dto.TipoPrecipitacion tipoPrecipitacion;

    /**
     * Estimación de precipitación en mm/h si está disponible (nullable).
     */
    private Double precipMmHora;
    /**
     * Temperatura en °C (informativa).
     */
    private Double temperatura;
}
