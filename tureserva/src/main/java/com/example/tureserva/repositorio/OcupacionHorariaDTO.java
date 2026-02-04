package com.example.tureserva.repositorio;

/**
 * Proyección para obtener ocupación horaria desde la base de datos.
 * dayOfWeek: 1=Monday .. 7=Sunday (ISO)
 * hour: 0..23
 * fecha: fecha específica (para vistas adaptativas)
 */
public interface OcupacionHorariaDTO {
    Integer getDayOfWeek();
    Integer getHour();
    Long getCount();
    java.time.LocalDate getFecha(); // Para vista adaptativa por fechas específicas
}
