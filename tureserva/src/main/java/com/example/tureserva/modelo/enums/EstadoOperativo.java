package com.example.tureserva.modelo.enums;

import lombok.Getter;

@Getter
public enum EstadoOperativo {
    DISPONIBLE("Disponible", true),
    MANTENIMIENTO("En Mantenimiento", false),
    CERRADO("Cerrado", false);
    
    private final String displayName;
    private final boolean disponibleParaReservas;
    
    EstadoOperativo(String displayName, boolean disponibleParaReservas) {
        this.displayName = displayName;
        this.disponibleParaReservas = disponibleParaReservas;
    }
}
