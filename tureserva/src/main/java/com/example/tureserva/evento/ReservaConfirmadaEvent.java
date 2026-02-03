package com.example.tureserva.evento;

import com.example.tureserva.modelo.Reserva;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Evento de dominio que se dispara cuando una reserva PENDIENTE es confirmada
 * por un administrador del complejo.
 * 
 * <p>Este evento marca el paso de PENDIENTE → CONFIRMADA, lo que indica que
 * la reserva ha sido aprobada y el pago de la seña ha sido verificado.
 * 
 * @author TuReserva
 * @since Fase 2 - Arquitectura Basada en Eventos
 */
@Getter
public class ReservaConfirmadaEvent extends ApplicationEvent {
    
    // Información de la reserva
    private final Long reservaId;
    private final String codigoReserva;
    private final Long clienteId;
    private final String clienteNombre;
    private final String clienteEmail;
    private final Long complejoId;
    private final String complejoNombre;
    
    // Contexto de confirmación
    private final LocalDateTime fechaConfirmacion;
    private final String observaciones;
    
    // Información del usuario que confirma
    private final String adminEmail;
    private final String adminRol;
    private final String ipAddress;
    
    // Snapshot de la reserva (para auditoría)
    private final Reserva reservaAntesDeConfirmar;
    
    /**
     * Constructor completo del evento de confirmación.
     * 
     * @param source El objeto que origina el evento (típicamente ServicioReserva)
     * @param reserva La reserva que fue confirmada (snapshot del estado anterior)
     * @param adminEmail Email del administrador que confirma
     * @param adminRol Rol del administrador
     * @param ipAddress Dirección IP desde donde se ejecutó la acción
     * @param observaciones Observaciones opcionales del administrador
     */
    public ReservaConfirmadaEvent(
            Object source,
            Reserva reserva,
            String adminEmail,
            String adminRol,
            String ipAddress,
            String observaciones) {
        
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
        
        // Contexto de confirmación
        this.fechaConfirmacion = LocalDateTime.now();
        this.observaciones = observaciones;
        
        // Usuario que confirma
        this.adminEmail = adminEmail;
        this.adminRol = adminRol;
        this.ipAddress = ipAddress;
        
        // Snapshot de la reserva
        this.reservaAntesDeConfirmar = reserva;
    }
    
    /**
     * Constructor simplificado sin observaciones.
     */
    public ReservaConfirmadaEvent(
            Object source,
            Reserva reserva,
            String adminEmail,
            String adminRol,
            String ipAddress) {
        
        this(source, reserva, adminEmail, adminRol, ipAddress, null);
    }
    
    /**
     * Genera una descripción detallada del evento para auditoría.
     */
    public String getDescripcionDetallada() {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("Reserva %s confirmada por administrador", codigoReserva));
        
        if (observaciones != null && !observaciones.isBlank()) {
            desc.append(" - Observaciones: ").append(observaciones);
        }
        
        return desc.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ReservaConfirmadaEvent[reservaId=%d, codigo=%s, cliente=%s, admin=%s]",
                reservaId, codigoReserva, clienteEmail, adminEmail);
    }
}
