package com.example.tureserva.listener;

import com.example.tureserva.evento.ReservaCreadaEvent;
import com.example.tureserva.modelo.TipoEvento;
import com.example.tureserva.servicio.ServicioAuditoria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener que registra eventos de auditoría para reservas
 * DESPUÉS del commit de la transacción principal.
 * 
 * Esto evita que problemas en la auditoría afecten la creación de reservas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditoriaReservaListener {
    
    private final ServicioAuditoria servicioAuditoria;
    
    /**
     * Registra la auditoría de creación de reserva después del commit exitoso.
     * Si falla la auditoría, no afecta la transacción principal.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservaCreada(ReservaCreadaEvent event) {
        log.info("🎯 LISTENER EJECUTADO - Recibido evento ReservaCreadaEvent: reservaId={}, complejoId={}, complejoNombre={}", 
                   event.getReservaId(), event.getComplejoId(), event.getComplejoNombre());
        
        try {
            log.info("📝 Iniciando registro de auditoría para reserva creada: {}", event.getReservaId());
            log.debug("Datos del evento - reservaId={}, codigoReserva={}, cliente={}, monto={}", 
                     event.getReservaId(), event.getCodigoReserva(), event.getClienteEmail(), event.getMontoTotal());
            
            // Crear objeto con datos legibles de la reserva creada
            var datosReserva = java.util.Map.of(
                "codigoReserva", event.getCodigoReserva(),
                "clienteEmail", event.getClienteEmail(),
                "montoTotal", event.getMontoTotal().toString(),
                "espaciosReservados", event.getEspaciosReservados(),
                "cantidadEspacios", event.getEspaciosReservados().size()
            );
            
            servicioAuditoria.registrarEvento(
                TipoEvento.RESERVA_CREADA,
                event.getComplejoId(),
                event.getComplejoNombre(),
                "Reserva " + event.getCodigoReserva() + " creada por " + event.getClienteEmail(),
                null, // datosAnteriores (no aplica para creación)
                datosReserva, // datosNuevos con información de la reserva
                "RESERVA",
                event.getReservaId(),
                null, // request (ya no está disponible después del commit)
                SecurityContextHolder.getContext().getAuthentication()
            );
            
            log.info("✅ Auditoría registrada correctamente para reserva {}", event.getReservaId());
            
        } catch (Exception e) {
            // Solo loguear el error, no propagar la excepción
            log.error("❌ Error al registrar auditoría de reserva creada {}: {}", 
                     event.getReservaId(), e.getMessage(), e);
        }
    }
}
