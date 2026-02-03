package com.example.tureserva.evento;

import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.MetodoPago;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento de dominio que se dispara cuando una reserva CONFIRMADA es finalizada
 * con el pago completo registrado.
 * 
 * <p>Este evento marca el paso de CONFIRMADA → FINALIZADA, indicando que
 * el servicio fue prestado y el pago total fue registrado por el complejo.
 * 
 * @author TuReserva
 * @since Fase 2 - Arquitectura Basada en Eventos
 */
@Getter
public class ReservaFinalizadaEvent extends ApplicationEvent {
    
    // Información de la reserva
    private final Long reservaId;
    private final String codigoReserva;
    private final Long clienteId;
    private final String clienteNombre;
    private final String clienteEmail;
    private final Long complejoId;
    private final String complejoNombre;
    
    // Contexto de finalización y pago
    private final LocalDateTime fechaFinalizacion;
    private final MetodoPago metodoPago;
    private final String numeroComprobante;
    private final BigDecimal montoTotalPagado;
    private final String notasPago;
    
    // Información del usuario que finaliza
    private final String adminEmail;
    private final String adminRol;
    private final String ipAddress;
    
    // Snapshot de la reserva (para auditoría)
    private final Reserva reservaAntesDeFinalizar;
    
    /**
     * Constructor completo del evento de finalización.
     * 
     * @param source El objeto que origina el evento (típicamente ServicioReserva)
     * @param reserva La reserva que fue finalizada (snapshot del estado anterior)
     * @param metodoPago Método de pago utilizado para el pago final
     * @param numeroComprobante Número de comprobante del pago
     * @param montoTotalPagado Monto total pagado
     * @param notasPago Notas adicionales sobre el pago
     * @param adminEmail Email del administrador que finaliza
     * @param adminRol Rol del administrador
     * @param ipAddress Dirección IP desde donde se ejecutó la acción
     */
    public ReservaFinalizadaEvent(
            Object source,
            Reserva reserva,
            MetodoPago metodoPago,
            String numeroComprobante,
            BigDecimal montoTotalPagado,
            String notasPago,
            String adminEmail,
            String adminRol,
            String ipAddress) {
        
        super(source);
        
        // Validaciones
        if (reserva == null) {
            throw new IllegalArgumentException("La reserva no puede ser nula");
        }
        
        // Extraer información de la reserva
        this.reservaId = reserva.getId();
        this.codigoReserva = reserva.getCodigoReserva();
        this.clienteId = reserva.getCliente() != null ? reserva.getCliente().getId() : null;
        this.clienteNombre = reserva.getCliente() != null ? reserva.getCliente().getNombre() : "N/A";
        this.clienteEmail = reserva.getCliente() != null ? reserva.getCliente().getEmail() : "N/A";
        
        // Extraer información del complejo (del primer detalle)
        if (reserva.getDetalles() != null && !reserva.getDetalles().isEmpty()) {
            var primerDetalle = reserva.getDetalles().get(0);
            this.complejoId = primerDetalle.getEspacioReservable() != null 
                && primerDetalle.getEspacioReservable().getComplejoDeportivo() != null
                ? primerDetalle.getEspacioReservable().getComplejoDeportivo().getId_complejo()
                : null;
            this.complejoNombre = primerDetalle.getEspacioReservable() != null 
                && primerDetalle.getEspacioReservable().getComplejoDeportivo() != null
                ? primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo()
                : "N/A";
        } else {
            this.complejoId = null;
            this.complejoNombre = "N/A";
        }
        
        // Contexto de finalización
        this.fechaFinalizacion = LocalDateTime.now();
        this.metodoPago = metodoPago;
        this.numeroComprobante = numeroComprobante;
        this.montoTotalPagado = montoTotalPagado != null ? montoTotalPagado : BigDecimal.ZERO;
        this.notasPago = notasPago;
        
        // Usuario que finaliza
        this.adminEmail = adminEmail;
        this.adminRol = adminRol;
        this.ipAddress = ipAddress;
        
        // Snapshot de la reserva
        this.reservaAntesDeFinalizar = reserva;
    }
    
    /**
     * Genera una descripción detallada del evento para auditoría.
     */
    public String getDescripcionDetallada() {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("Reserva %s finalizada con pago completo", codigoReserva));
        
        if (metodoPago != null) {
            desc.append(" - Método: ").append(metodoPago.name());
        }
        
        if (numeroComprobante != null && !numeroComprobante.isBlank()) {
            desc.append(" - Comprobante: ").append(numeroComprobante);
        }
        
        if (montoTotalPagado != null && montoTotalPagado.compareTo(BigDecimal.ZERO) > 0) {
            desc.append(String.format(" - Monto: $%.2f", montoTotalPagado));
        }
        
        if (notasPago != null && !notasPago.isBlank()) {
            desc.append(" - Notas: ").append(notasPago);
        }
        
        return desc.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ReservaFinalizadaEvent[reservaId=%d, codigo=%s, cliente=%s, monto=$%.2f, metodo=%s]",
                reservaId, codigoReserva, clienteEmail, montoTotalPagado, metodoPago);
    }
}
