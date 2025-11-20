package com.example.tureserva.servicio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class EmailDetalleDTO {
    private Long id;
    private String espacioNombre;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private java.math.BigDecimal duracionHoras;
    private BigDecimal precioPorHora;
    private BigDecimal subtotal;
    private List<EmailServicioAdicionalDTO> servicios = new ArrayList<>();

    // Politica de cancelación (si aplica)
    private String politicaCancelacionNombre;
    private Integer politicaHorasAnticipacion;
    private Double politicaPorcentajeDevolucion;

    public EmailDetalleDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEspacioNombre() { return espacioNombre; }
    public void setEspacioNombre(String espacioNombre) { this.espacioNombre = espacioNombre; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public java.math.BigDecimal getDuracionHoras() { return duracionHoras; }
    public void setDuracionHoras(java.math.BigDecimal duracionHoras) { this.duracionHoras = duracionHoras; }

    public BigDecimal getPrecioPorHora() { return precioPorHora; }
    public void setPrecioPorHora(BigDecimal precioPorHora) { this.precioPorHora = precioPorHora; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public List<EmailServicioAdicionalDTO> getServicios() { return servicios; }
    public void setServicios(List<EmailServicioAdicionalDTO> servicios) { this.servicios = servicios; }

    public String getPoliticaCancelacionNombre() { return politicaCancelacionNombre; }
    public void setPoliticaCancelacionNombre(String politicaCancelacionNombre) { this.politicaCancelacionNombre = politicaCancelacionNombre; }

    public Integer getPoliticaHorasAnticipacion() { return politicaHorasAnticipacion; }
    public void setPoliticaHorasAnticipacion(Integer politicaHorasAnticipacion) { this.politicaHorasAnticipacion = politicaHorasAnticipacion; }

    public Double getPoliticaPorcentajeDevolucion() { return politicaPorcentajeDevolucion; }
    public void setPoliticaPorcentajeDevolucion(Double politicaPorcentajeDevolucion) { this.politicaPorcentajeDevolucion = politicaPorcentajeDevolucion; }
}
