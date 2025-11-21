package com.example.tureserva.servicio.dto;

import java.math.BigDecimal;

/**
 * DTO simple para el reporte financiero por espacio
 */
public class ReporteFinancieroDTO {
    private String espacioNombre;
    private String tipoEspacio;
    private Long cantidadReservas;
    private BigDecimal ingresos;

    public ReporteFinancieroDTO() {}

    public ReporteFinancieroDTO(String espacioNombre, String tipoEspacio, Long cantidadReservas, BigDecimal ingresos) {
        this.espacioNombre = espacioNombre;
        this.tipoEspacio = tipoEspacio;
        this.cantidadReservas = cantidadReservas;
        this.ingresos = ingresos;
    }

    public String getEspacioNombre() { return espacioNombre; }
    public void setEspacioNombre(String espacioNombre) { this.espacioNombre = espacioNombre; }

    public String getTipoEspacio() { return tipoEspacio; }
    public void setTipoEspacio(String tipoEspacio) { this.tipoEspacio = tipoEspacio; }

    public Long getCantidadReservas() { return cantidadReservas; }
    public void setCantidadReservas(Long cantidadReservas) { this.cantidadReservas = cantidadReservas; }

    public BigDecimal getIngresos() { return ingresos; }
    public void setIngresos(BigDecimal ingresos) { this.ingresos = ingresos; }
}
