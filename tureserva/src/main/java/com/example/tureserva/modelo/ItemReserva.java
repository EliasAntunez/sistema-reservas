package com.example.tureserva.modelo;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Representa un item individual dentro de una reserva temporal (en sesión).
 * Cada ItemReserva corresponde a un espacio con su horario específico.
 * 
 * Se usa durante el flujo de creación de reservas para permitir que el cliente
 * agregue múltiples espacios antes de confirmar.
 * 
 * @author TuReserva
 */
public class ItemReserva implements Serializable {
    
    private Long espacioId;
    private LocalTime horaInicio;
    private int duracionHoras; // 1, 2 o 3
    
    // Datos adicionales para mostrar en la UI (no se persisten, solo para sesión)
    private String nombreEspacio;
    private String tipoEspacio;
    private Double precioPorHora;
    
    public ItemReserva() {
        this.duracionHoras = 1;
    }
    
    public ItemReserva(Long espacioId, LocalTime horaInicio) {
        this.espacioId = espacioId;
        this.horaInicio = horaInicio;
        this.duracionHoras = 1;
    }
    
    public ItemReserva(Long espacioId, LocalTime horaInicio, int duracionHoras) {
        this.espacioId = espacioId;
        this.horaInicio = horaInicio;
        this.duracionHoras = duracionHoras;
    }
    
    // ==================== GETTERS Y SETTERS ====================
    
    public Long getEspacioId() {
        return espacioId;
    }
    
    public void setEspacioId(Long espacioId) {
        this.espacioId = espacioId;
    }
    
    public LocalTime getHoraInicio() {
        return horaInicio;
    }
    
    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }
    
    public int getDuracionHoras() {
        return duracionHoras;
    }
    
    public void setDuracionHoras(int duracionHoras) {
        this.duracionHoras = duracionHoras;
    }
    
    public String getNombreEspacio() {
        return nombreEspacio;
    }
    
    public void setNombreEspacio(String nombreEspacio) {
        this.nombreEspacio = nombreEspacio;
    }
    
    public String getTipoEspacio() {
        return tipoEspacio;
    }
    
    public void setTipoEspacio(String tipoEspacio) {
        this.tipoEspacio = tipoEspacio;
    }
    
    public Double getPrecioPorHora() {
        return precioPorHora;
    }
    
    public void setPrecioPorHora(Double precioPorHora) {
        this.precioPorHora = precioPorHora;
    }
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    /**
     * Calcula la hora de fin basada en horaInicio + duracionHoras.
     */
    public LocalTime getHoraFin() {
        if (horaInicio == null) {
            return null;
        }
        return horaInicio.plusHours(duracionHoras);
    }
    
    /**
     * Extiende la duración en 1 hora (máximo 3).
     */
    public boolean extenderUnaHora() {
        if (duracionHoras < 3) {
            duracionHoras++;
            return true;
        }
        return false;
    }
    
    /**
     * Reduce la duración en 1 hora (mínimo 1).
     */
    public boolean reducirUnaHora() {
        if (duracionHoras > 1) {
            duracionHoras--;
            return true;
        }
        return false;
    }
    
    /**
     * Verifica si se puede extender más (no ha llegado al máximo de 3h).
     */
    public boolean puedeExtender() {
        return duracionHoras < 3;
    }
    
    /**
     * Verifica si se puede reducir más (no ha llegado al mínimo de 1h).
     */
    public boolean puedeReducir() {
        return duracionHoras > 1;
    }
    
    /**
     * Calcula el subtotal de este item (precioPorHora * duracionHoras).
     */
    public Double calcularSubtotal() {
        if (precioPorHora == null) {
            return 0.0;
        }
        return precioPorHora * duracionHoras;
    }
    
    @Override
    public String toString() {
        return "ItemReserva{" +
                "espacioId=" + espacioId +
                ", nombreEspacio='" + nombreEspacio + '\'' +
                ", horaInicio=" + horaInicio +
                ", duracionHoras=" + duracionHoras +
                ", horaFin=" + getHoraFin() +
                '}';
    }
}
