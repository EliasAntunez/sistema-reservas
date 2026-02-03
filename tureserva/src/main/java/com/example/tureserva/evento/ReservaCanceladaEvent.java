package com.example.tureserva.evento;

import com.example.tureserva.modelo.Reserva;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento de dominio que se dispara cuando una reserva es cancelada.
 * Contiene todo el contexto necesario para auditoría y notificaciones.
 * 
 * <p>Este evento es parte de la Fase 2 de mejoras del sistema de auditoría,
 * migrando de AOP a eventos de dominio para reservas.
 * 
 * @author TuReserva
 * @since Fase 2 - Arquitectura Basada en Eventos
 */
@Getter
public class ReservaCanceladaEvent extends ApplicationEvent {
    
    // Información de la reserva
    private final Long reservaId;
    private final String codigoReserva;
    private final Long clienteId;
    private final String clienteNombre;
    private final String clienteEmail;
    private final Long complejoId;
    private final String complejoNombre;
    
    // Contexto de cancelación
    private final String motivoCancelacion;
    private final BigDecimal montoDevuelto;
    private final BigDecimal montoPenalizado;
    private final boolean fueraDeTerminos;
    private final LocalDateTime fechaCancelacion;
    
    // Información del usuario que realiza la acción
    private final String usuarioEmail;
    private final String usuarioRol;
    private final String ipAddress;
    
    // Snapshot de la reserva (para auditoría)
    private final Reserva reservaAntesDeCancelar;
    
    /**
     * Constructor completo del evento de cancelación.
     * 
     * @param source El objeto que origina el evento (típicamente ServicioReserva)
     * @param reserva La reserva que fue cancelada (snapshot del estado anterior)
     * @param motivo Motivo de la cancelación
     * @param montoDevuelto Monto devuelto al cliente
     * @param montoPenalizado Monto de penalización aplicado
     * @param fueraDeTerminos Si la cancelación está fuera de los términos de la política
     * @param usuarioEmail Email del usuario que ejecuta la cancelación
     * @param usuarioRol Rol del usuario (CLIENTE, ADMIN_COMPLEJO, etc.)
     * @param ipAddress Dirección IP desde donde se ejecutó la acción
     */
    public ReservaCanceladaEvent(
            Object source,
            Reserva reserva,
            String motivo,
            BigDecimal montoDevuelto,
            BigDecimal montoPenalizado,
            boolean fueraDeTerminos,
            String usuarioEmail,
            String usuarioRol,
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
        
        // Contexto de cancelación
        this.motivoCancelacion = motivo;
        this.montoDevuelto = montoDevuelto != null ? montoDevuelto : BigDecimal.ZERO;
        this.montoPenalizado = montoPenalizado != null ? montoPenalizado : BigDecimal.ZERO;
        this.fueraDeTerminos = fueraDeTerminos;
        this.fechaCancelacion = LocalDateTime.now();
        
        // Usuario
        this.usuarioEmail = usuarioEmail;
        this.usuarioRol = usuarioRol;
        this.ipAddress = ipAddress;
        
        // Snapshot de la reserva
        this.reservaAntesDeCancelar = reserva;
    }
    
    /**
     * Constructor simplificado para casos donde no hay devolución ni penalización
     * (por ejemplo, cancelación de reservas PENDIENTES sin seña pagada).
     */
    public ReservaCanceladaEvent(
            Object source,
            Reserva reserva,
            String motivo,
            String usuarioEmail,
            String usuarioRol,
            String ipAddress) {
        
        this(source, reserva, motivo, BigDecimal.ZERO, BigDecimal.ZERO, false, 
             usuarioEmail, usuarioRol, ipAddress);
    }
    
    /**
     * Genera una descripción detallada del evento para auditoría.
     */
    public String getDescripcionDetallada() {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("Reserva %s cancelada", codigoReserva));
        
        if (motivoCancelacion != null && !motivoCancelacion.isBlank()) {
            desc.append(" - Motivo: ").append(motivoCancelacion);
        }
        
        if (montoDevuelto.compareTo(BigDecimal.ZERO) > 0) {
            desc.append(String.format(" - Devolución: $%.2f", montoDevuelto));
        }
        
        if (montoPenalizado.compareTo(BigDecimal.ZERO) > 0) {
            desc.append(String.format(" - Penalización: $%.2f", montoPenalizado));
        }
        
        if (fueraDeTerminos) {
            desc.append(" - Fuera de términos");
        }
        
        return desc.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ReservaCanceladaEvent[reservaId=%d, codigo=%s, cliente=%s, motivo=%s, devolucion=$%.2f, penalizacion=$%.2f]",
                reservaId, codigoReserva, clienteEmail, motivoCancelacion, montoDevuelto, montoPenalizado);
    }
}
