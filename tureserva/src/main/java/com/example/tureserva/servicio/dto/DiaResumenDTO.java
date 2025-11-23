package com.example.tureserva.servicio.dto;

import java.util.List;

public class DiaResumenDTO {
    private int diaIso; // 1=Lunes .. 7=Domingo
    private String nombreDia;
    private List<HorarioCountDTO> horarios;

    public DiaResumenDTO() {}

    public DiaResumenDTO(int diaIso, String nombreDia, List<HorarioCountDTO> horarios) {
        this.diaIso = diaIso;
        this.nombreDia = nombreDia;
        this.horarios = horarios;
    }

    public int getDiaIso() { return diaIso; }
    public void setDiaIso(int diaIso) { this.diaIso = diaIso; }

    public String getNombreDia() { return nombreDia; }
    public void setNombreDia(String nombreDia) { this.nombreDia = nombreDia; }

    public List<HorarioCountDTO> getHorarios() { return horarios; }
    public void setHorarios(List<HorarioCountDTO> horarios) { this.horarios = horarios; }
}
