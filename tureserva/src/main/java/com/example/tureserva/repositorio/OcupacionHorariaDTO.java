package com.example.tureserva.repositorio;

/**
 * Proyección para obtener ocupación horaria desde la base de datos.
 * dayOfWeek: 1=Monday .. 7=Sunday (ISO)
 * hour: 0..23
 */
public interface OcupacionHorariaDTO {
    Integer getDayOfWeek();
    Integer getHour();
    Long getCount();
}
