package com.example.tureserva.modelo;

import java.time.LocalTime;

/**
 * DTO (Data Transfer Object) que representa un horario de inicio disponible.
 * Utilizado para mostrar los horarios en los que se puede iniciar una reserva.
 * 
 * @param horaInicio Hora de inicio disponible (ej: 15:00, 16:00, 17:00)
 * @param disponible true si el horario está libre, false si está ocupado
 */
public record IntervaloDisponible(
    LocalTime horaInicio,
    boolean disponible
) {
}
