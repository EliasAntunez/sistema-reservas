package com.example.tureserva.servicio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EmailReservaDTO {
    private String nombreCliente;
    private String codigoReserva;
    private LocalDate fecha;
    private String nombreComplejo;
    private BigDecimal montoTotal;
    private java.util.List<com.example.tureserva.servicio.dto.EmailDetalleDTO> detalles = new java.util.ArrayList<>();
    private java.util.List<String> recordatorios = new java.util.ArrayList<>();
    private String politicaCancelacionResumen;

    public EmailReservaDTO() {}

    public EmailReservaDTO(String nombreCliente, String codigoReserva, LocalDate fecha, String nombreComplejo, BigDecimal montoTotal) {
        this.nombreCliente = nombreCliente;
        this.codigoReserva = codigoReserva;
        this.fecha = fecha;
        this.nombreComplejo = nombreComplejo;
        this.montoTotal = montoTotal;
    }

    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String nombreCliente) { this.nombreCliente = nombreCliente; }

    public String getCodigoReserva() { return codigoReserva; }
    public void setCodigoReserva(String codigoReserva) { this.codigoReserva = codigoReserva; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getNombreComplejo() { return nombreComplejo; }
    public void setNombreComplejo(String nombreComplejo) { this.nombreComplejo = nombreComplejo; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }
    public java.util.List<com.example.tureserva.servicio.dto.EmailDetalleDTO> getDetalles() { return detalles; }
    public void setDetalles(java.util.List<com.example.tureserva.servicio.dto.EmailDetalleDTO> detalles) { this.detalles = detalles; }

    public java.util.List<String> getRecordatorios() { return recordatorios; }
    public void setRecordatorios(java.util.List<String> recordatorios) { this.recordatorios = recordatorios; }

    public String getPoliticaCancelacionResumen() { return politicaCancelacionResumen; }
    public void setPoliticaCancelacionResumen(String politicaCancelacionResumen) { this.politicaCancelacionResumen = politicaCancelacionResumen; }
}
