package com.example.tureserva.servicio.dto;

import java.util.List;

/**
 * Contiene la matriz de ocupación y metadatos para la vista.
 * matrix: List de filas (cada fila corresponde a una hora) donde cada fila tiene 7 valores (Lun..Dom).
 */
public class OcupacionMatrixDTO {
    private List<Integer> horas; // orden de filas (ej: 8..23,0)
    private List<List<Long>> matrix; // matrix.size() == horas.size(), each row length == 7
    private long maxCount;
    private java.util.List<java.util.List<String>> cellClasses; // parallel structure to matrix with CSS class names
    // Totales por día (Lun..Dom) y total general
    private List<Long> totalesPorDia; // size == 7
    private long totalGeneral;
    private java.util.List<DiaResumenDTO> resumenPorDia;
    private List<Long> totalesPorHora; // paralelo a horas
    private java.util.Map<String, Object> analisisHorarios; // análisis inteligente de patrones
    private List<String> nombresColumnas; // nombres dinámicos de columnas (fechas, semanas, meses)
    private String tipoVista; // "especifica", "semanal", "mensual"

    public OcupacionMatrixDTO(List<Integer> horas, List<List<Long>> matrix, long maxCount) {
        this.horas = horas;
        this.matrix = matrix;
        this.maxCount = maxCount;
        this.totalesPorDia = java.util.Collections.nCopies(7, 0L);
        this.totalGeneral = 0L;
        this.resumenPorDia = new java.util.ArrayList<>();
        this.totalesPorHora = (matrix != null) ? new java.util.ArrayList<>() : java.util.Collections.emptyList();
        if (matrix != null) {
            for (int ri = 0; ri < matrix.size(); ri++) this.totalesPorHora.add(0L);
        }
        // initialize empty cellClasses with same dimensions as matrix
        if (matrix != null) {
            this.cellClasses = new java.util.ArrayList<>();
            for (int ri = 0; ri < matrix.size(); ri++) {
                java.util.List<String> r = new java.util.ArrayList<>();
                for (int i = 0; i < 7; i++) r.add("");
                this.cellClasses.add(r);
            }
        } else {
            this.cellClasses = java.util.Collections.emptyList();
        }
    }

    public List<Integer> getHoras() { return horas; }
    public List<List<Long>> getMatrix() { return matrix; }
    public long getMaxCount() { return maxCount; }
    public List<Long> getTotalesPorDia() { return totalesPorDia; }
    public void setTotalesPorDia(List<Long> totalesPorDia) { this.totalesPorDia = totalesPorDia; }
    public long getTotalGeneral() { return totalGeneral; }
    public void setTotalGeneral(long totalGeneral) { this.totalGeneral = totalGeneral; }

    public java.util.List<DiaResumenDTO> getResumenPorDia() { return resumenPorDia; }
    public void setResumenPorDia(java.util.List<DiaResumenDTO> resumenPorDia) { this.resumenPorDia = resumenPorDia; }

    public List<Long> getTotalesPorHora() { return totalesPorHora; }
    public void setTotalesPorHora(List<Long> totalesPorHora) { this.totalesPorHora = totalesPorHora; }

    public java.util.Map<String, Object> getAnalisisHorarios() { return analisisHorarios; }
    public void setAnalisisHorarios(java.util.Map<String, Object> analisisHorarios) { this.analisisHorarios = analisisHorarios; }

    public List<String> getNombresColumnas() { return nombresColumnas; }
    public void setNombresColumnas(List<String> nombresColumnas) { this.nombresColumnas = nombresColumnas; }
    
    public String getTipoVista() { return tipoVista; }
    public void setTipoVista(String tipoVista) { this.tipoVista = tipoVista; }

    public java.util.List<java.util.List<String>> getCellClasses() { return cellClasses; }
    public void setCellClasses(java.util.List<java.util.List<String>> cellClasses) { this.cellClasses = cellClasses; }
}
