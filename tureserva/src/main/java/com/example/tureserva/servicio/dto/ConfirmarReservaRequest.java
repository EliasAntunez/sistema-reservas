package com.example.tureserva.servicio.dto;

import com.example.tureserva.modelo.DatosReservaTemp;
import java.util.Map;

/**
 * Request para confirmar una reserva luego de que el pago (seña) fue aprobado.
 * Ahora soporta un mapa de servicios por item para que el servidor pueda
 * calcular el monto real incluyendo servicios adicionales.
 */
public class ConfirmarReservaRequest {
    private Long pagoId;
    private DatosReservaTemp datosReserva;

    /**
     * Mapa: key = índice del item en la lista `datosReserva.items`,
     * value = mapa (idServicio -> cantidad)
     */
    private Map<Integer, Map<Long, Integer>> serviciosPorItem;

    public Long getPagoId() { return pagoId; }
    public void setPagoId(Long pagoId) { this.pagoId = pagoId; }

    public DatosReservaTemp getDatosReserva() { return datosReserva; }
    public void setDatosReserva(DatosReservaTemp datosReserva) { this.datosReserva = datosReserva; }

    public Map<Integer, Map<Long, Integer>> getServiciosPorItem() { return serviciosPorItem; }
    public void setServiciosPorItem(Map<Integer, Map<Long, Integer>> serviciosPorItem) { this.serviciosPorItem = serviciosPorItem; }
}
