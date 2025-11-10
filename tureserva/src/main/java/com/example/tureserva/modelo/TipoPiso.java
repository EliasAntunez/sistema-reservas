package com.example.tureserva.modelo;

public enum TipoPiso {
    CESPED("Césped"),
    CESPED_SINTETICO("Césped Sintético"),
    PARQUET_MADERA("Parquet de Madera"),
    CEMENTO("Cemento"), ARENA("Arena"),
    OTRO("Otro");
    
    private final String descripcion;
    
    TipoPiso(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
