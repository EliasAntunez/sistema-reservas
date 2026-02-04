package com.example.tureserva.servicio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para reporte financiero temporal (agrupado por fecha).
 * Representa los ingresos diarios/semanales/mensuales en un periodo.
 * Usado para gráficos de evolución temporal con eje X = tiempo.
 */
public class ReporteFinancieroTemporalDTO {
    
    private LocalDate fecha;
    private Long cantidadReservas;
    private BigDecimal ingresos;

    /**
     * Constructor utilizado por JPA en queries JPQL
     */
    public ReporteFinancieroTemporalDTO(LocalDate fecha, Long cantidadReservas, BigDecimal ingresos) {
        this.fecha = fecha;
        this.cantidadReservas = cantidadReservas;
        this.ingresos = ingresos;
    }
    
    /**
     * Constructor alternativo para queries que usan java.sql.Date en lugar de LocalDate
     */
    public ReporteFinancieroTemporalDTO(java.sql.Date fecha, Long cantidadReservas, BigDecimal ingresos) {
        this.fecha = fecha != null ? fecha.toLocalDate() : null;
        this.cantidadReservas = cantidadReservas;
        this.ingresos = ingresos;
    }

    // Getters y Setters
    
    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Long getCantidadReservas() {
        return cantidadReservas;
    }

    public void setCantidadReservas(Long cantidadReservas) {
        this.cantidadReservas = cantidadReservas;
    }

    public BigDecimal getIngresos() {
        return ingresos;
    }

    public void setIngresos(BigDecimal ingresos) {
        this.ingresos = ingresos;
    }

    @Override
    public String toString() {
        return "ReporteFinancieroTemporalDTO{" +
                "fecha=" + fecha +
                ", cantidadReservas=" + cantidadReservas +
                ", ingresos=" + ingresos +
                '}';
    }
}
