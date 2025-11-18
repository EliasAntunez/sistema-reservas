package com.example.tureserva.modelo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DTO para almacenar datos temporales de una reserva en sesión HTTP.
 * Se usa durante el flujo de selección y confirmación de reserva.
 * 
 * AHORA soporta múltiples espacios en una misma reserva.
 * El cliente puede agregar varios espacios (mismo complejo, misma fecha) antes de confirmar.
 */
public class DatosReservaTemp implements Serializable {
    
    private Long complejoId;
    private LocalDate fecha;
    private List<ItemReserva> items; // Lista de espacios con sus horarios
    
    public DatosReservaTemp() {
        this.items = new ArrayList<>();
    }
    
    public DatosReservaTemp(Long complejoId, LocalDate fecha) {
        this.complejoId = complejoId;
        this.fecha = fecha;
        this.items = new ArrayList<>();
    }
    
    // ==================== GETTERS Y SETTERS ====================
    
    public Long getComplejoId() {
        return complejoId;
    }
    
    public void setComplejoId(Long complejoId) {
        this.complejoId = complejoId;
    }
    
    public LocalDate getFecha() {
        return fecha;
    }
    
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }
    
    public List<ItemReserva> getItems() {
        return items;
    }
    
    public void setItems(List<ItemReserva> items) {
        this.items = items;
    }
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    /**
     * Agrega un nuevo item (espacio + horario) a la reserva temporal.
     */
    public void agregarItem(ItemReserva item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
    }
    
    /**
     * Remueve un item por su índice en la lista.
     */
    public boolean removerItem(int indice) {
        if (items != null && indice >= 0 && indice < items.size()) {
            items.remove(indice);
            return true;
        }
        return false;
    }
    
    /**
     * Remueve un item por el ID del espacio.
     */
    public boolean removerItemPorEspacioId(Long espacioId) {
        if (items == null) {
            return false;
        }
        return items.removeIf(item -> item.getEspacioId().equals(espacioId));
    }
    
    /**
     * Obtiene un item específico por índice.
     */
    public Optional<ItemReserva> getItem(int indice) {
        if (items != null && indice >= 0 && indice < items.size()) {
            return Optional.of(items.get(indice));
        }
        return Optional.empty();
    }
    
    /**
     * Busca un item por el ID del espacio.
     */
    public Optional<ItemReserva> getItemPorEspacioId(Long espacioId) {
        if (items == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(item -> item.getEspacioId().equals(espacioId))
                .findFirst();
    }
    
    /**
     * Verifica si la reserva tiene al menos un espacio agregado.
     */
    public boolean tieneEspacios() {
        return items != null && !items.isEmpty();
    }
    
    /**
     * Obtiene la cantidad de espacios agregados.
     */
    public int cantidadEspacios() {
        return items != null ? items.size() : 0;
    }
    
    /**
     * Calcula el monto total de la reserva (suma de todos los items).
     */
    public BigDecimal calcularMontoTotal() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return items.stream()
                .map(ItemReserva::calcularSubtotal)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Limpia todos los items de la reserva.
     */
    public void limpiarItems() {
        if (items != null) {
            items.clear();
        }
    }
    
    /**
     * Verifica si ya existe un espacio con el mismo ID y horario.
     */
    public boolean existeEspacioConHorario(Long espacioId, LocalTime horaInicio) {
        if (items == null) {
            return false;
        }
        return items.stream()
                .anyMatch(item -> item.getEspacioId().equals(espacioId) 
                        && item.getHoraInicio().equals(horaInicio));
    }
    
    @Override
    public String toString() {
        return "DatosReservaTemp{" +
                "complejoId=" + complejoId +
                ", fecha=" + fecha +
                ", cantidadItems=" + cantidadEspacios() +
                ", montoTotal=" + calcularMontoTotal() +
                '}';
    }
}
