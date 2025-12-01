package com.example.tureserva.servicio.dto;

/**
 * Tipos simplificados de precipitación derivados del código WMO.
 * Nomenclatura adaptada para Argentina.
 */
public enum TipoPrecipitacion {
    LLOVIZNA,           // Lluvia muy ligera
    LLUVIA,             // Lluvia moderada
    CHAPARRON,          // Lluvia fuerte e intensa (antes: CHUBASCOS)
    TORMENTA,           // Tormenta eléctrica
    NIEVE,              // Nieve
    OTRO                // Otros fenómenos
}
