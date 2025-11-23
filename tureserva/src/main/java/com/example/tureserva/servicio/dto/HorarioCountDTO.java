package com.example.tureserva.servicio.dto;

public class HorarioCountDTO {
    private int hora;
    private long count;

    public HorarioCountDTO() {}

    public HorarioCountDTO(int hora, long count) {
        this.hora = hora;
        this.count = count;
    }

    public int getHora() { return hora; }
    public void setHora(int hora) { this.hora = hora; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
