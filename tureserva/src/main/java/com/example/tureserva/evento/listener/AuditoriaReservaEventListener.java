package com.example.tureserva.evento.listener;

import com.example.tureserva.evento.ReservaCanceladaEvent;
import com.example.tureserva.evento.ReservaConfirmadaEvent;
import com.example.tureserva.evento.ReservaFinalizadaEvent;
import com.example.tureserva.modelo.TipoEvento;
import com.example.tureserva.servicio.ServicioAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener de eventos de dominio de Reservas que registra automáticamente
 * en el sistema de auditoría.
 * 
 * <p>Este componente implementa la Fase 2 del sistema de auditoría, donde
 * migramos de AOP interceptors a eventos de dominio para tener un modelo
 * más explícito y mantenible.
 * 
 * <p><b>Ventajas sobre AOP:</b>
 * <ul>
 *   <li>Contexto más rico: Los eventos contienen información específica del dominio</li>
 *   <li>Desacoplamiento: El código de negocio no está acoplado a auditoría</li>
 *   <li>Testeable: Los eventos pueden testearse independientemente</li>
 *   <li>Asíncrono: Auditoría no bloquea la transacción principal</li>
 *   <li>Explícito: El código muestra claramente qué se está auditando</li>
 * </ul>
 * 
 * <p>Los listeners se ejecutan después de que la transacción se confirma exitosamente
 * (@TransactionalEventListener con AFTER_COMMIT), garantizando que solo auditamos
 * operaciones que realmente se persistieron.
 * 
 * @author TuReserva
 * @since Fase 2 - Arquitectura Basada en Eventos
 */
@Component
public class AuditoriaReservaEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaReservaEventListener.class);
    
    private final ServicioAuditoria servicioAuditoria;
    
    public AuditoriaReservaEventListener(ServicioAuditoria servicioAuditoria) {
        this.servicioAuditoria = servicioAuditoria;
    }
    
    /**
     * Escucha eventos de reserva cancelada y registra en auditoría.
     * 
     * <p>Se ejecuta DESPUÉS de que la transacción se confirma exitosamente,
     * garantizando que solo auditamos cancelaciones que realmente se persistieron.
     * 
     * @param event Evento de cancelación con todo el contexto
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void manejarReservaCancelada(ReservaCanceladaEvent event) {
        try {
            logger.info("📝 Registrando auditoría de cancelación - Reserva: {}, Cliente: {}", 
                    event.getCodigoReserva(), event.getClienteEmail());
            
            // Construir datos simplificados de la reserva ANTES de cancelar
            Map<String, Object> datosAnteriores = new HashMap<>();
            datosAnteriores.put("reservaId", event.getReservaId());
            datosAnteriores.put("codigoReserva", event.getCodigoReserva());
            datosAnteriores.put("clienteEmail", event.getClienteEmail());
            datosAnteriores.put("estadoAnterior", "CONFIRMADA o PENDIENTE");
            
            // Construir datos específicos de cancelación
            Map<String, Object> datosCancelacion = new HashMap<>();
            datosCancelacion.put("estadoNuevo", "CANCELADA");
            datosCancelacion.put("motivoCancelacion", event.getMotivoCancelacion());
            datosCancelacion.put("montoDevuelto", event.getMontoDevuelto().toString());
            datosCancelacion.put("montoPenalizado", event.getMontoPenalizado().toString());
            datosCancelacion.put("fueraDeTerminos", event.isFueraDeTerminos());
            datosCancelacion.put("fechaCancelacion", event.getFechaCancelacion().toString());
            
            // Registrar en auditoría con contexto completo
            servicioAuditoria.registrarEvento(
                TipoEvento.RESERVA_CANCELADA,
                event.getComplejoId(),
                event.getComplejoNombre(),
                "RESERVA",
                event.getReservaId(),
                datosAnteriores,     // Estado ANTES (simplificado)
                datosCancelacion,     // Estado DESPUÉS (con info de cancelación)
                event.getDescripcionDetallada(),
                event.getUsuarioEmail(),
                event.getClienteNombre(),  // Nombre del cliente que cancela
                event.getUsuarioRol(),
                event.getIpAddress()
            );
            
            logger.debug("✅ Auditoría de cancelación registrada exitosamente - Reserva: {}", 
                    event.getReservaId());
            
        } catch (Exception e) {
            // No propagamos la excepción para no afectar la transacción principal
            logger.error("❌ Error al registrar auditoría de cancelación - Reserva: {}", 
                    event.getReservaId(), e);
        }
    }
    
    /**
     * Escucha eventos de reserva confirmada y registra en auditoría.
     * 
     * @param event Evento de confirmación con todo el contexto
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void manejarReservaConfirmada(ReservaConfirmadaEvent event) {
        try {
            logger.info("📝 Registrando auditoría de confirmación - Reserva: {}, Admin: {}", 
                    event.getCodigoReserva(), event.getAdminEmail());
            
            // Construir datos simplificados ANTES de confirmar
            Map<String, Object> datosAnteriores = new HashMap<>();
            datosAnteriores.put("reservaId", event.getReservaId());
            datosAnteriores.put("codigoReserva", event.getCodigoReserva());
            datosAnteriores.put("clienteEmail", event.getClienteEmail());
            datosAnteriores.put("estadoAnterior", "PENDIENTE");
            
            // Construir datos específicos de confirmación
            Map<String, Object> datosConfirmacion = new HashMap<>();
            datosConfirmacion.put("estadoNuevo", "CONFIRMADA");
            datosConfirmacion.put("fechaConfirmacion", event.getFechaConfirmacion().toString());
            if (event.getObservaciones() != null && !event.getObservaciones().isBlank()) {
                datosConfirmacion.put("observaciones", event.getObservaciones());
            }
            
            // Registrar en auditoría
            servicioAuditoria.registrarEvento(
                TipoEvento.RESERVA_CONFIRMADA,
                event.getComplejoId(),
                event.getComplejoNombre(),
                "RESERVA",
                event.getReservaId(),
                datosAnteriores,      // Estado ANTES (simplificado)
                datosConfirmacion,    // Estado DESPUÉS (CONFIRMADA)
                event.getDescripcionDetallada(),
                event.getAdminEmail(),
                event.getAdminEmail(),  // Usar email como nombre si no hay nombre disponible
                event.getAdminRol(),
                event.getIpAddress()
            );
            
            logger.debug("✅ Auditoría de confirmación registrada exitosamente - Reserva: {}", 
                    event.getReservaId());
            
        } catch (Exception e) {
            logger.error("❌ Error al registrar auditoría de confirmación - Reserva: {}", 
                    event.getReservaId(), e);
        }
    }
    
    /**
     * Escucha eventos de reserva finalizada y registra en auditoría.
     * 
     * @param event Evento de finalización con todo el contexto
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void manejarReservaFinalizada(ReservaFinalizadaEvent event) {
        try {
            logger.info("📝 Registrando auditoría de finalización - Reserva: {}, Monto: ${}, Admin: {}", 
                    event.getCodigoReserva(), event.getMontoTotalPagado(), event.getAdminEmail());
            
            // Construir datos simplificados ANTES de finalizar
            Map<String, Object> datosAnteriores = new HashMap<>();
            datosAnteriores.put("reservaId", event.getReservaId());
            datosAnteriores.put("codigoReserva", event.getCodigoReserva());
            datosAnteriores.put("clienteEmail", event.getClienteEmail());
            datosAnteriores.put("estadoAnterior", "CONFIRMADA");
            
            // Construir datos específicos de finalización
            Map<String, Object> datosFinalizacion = new HashMap<>();
            datosFinalizacion.put("estadoNuevo", "FINALIZADA");
            datosFinalizacion.put("fechaFinalizacion", event.getFechaFinalizacion().toString());
            datosFinalizacion.put("metodoPago", event.getMetodoPago() != null ? event.getMetodoPago().name() : "N/A");
            datosFinalizacion.put("numeroComprobante", event.getNumeroComprobante());
            datosFinalizacion.put("montoTotalPagado", event.getMontoTotalPagado().toString());
            if (event.getNotasPago() != null && !event.getNotasPago().isBlank()) {
                datosFinalizacion.put("notasPago", event.getNotasPago());
            }
            
            // Registrar en auditoría
            servicioAuditoria.registrarEvento(
                TipoEvento.RESERVA_FINALIZADA,
                event.getComplejoId(),
                event.getComplejoNombre(),
                "RESERVA",
                event.getReservaId(),
                datosAnteriores,      // Estado ANTES (simplificado)
                datosFinalizacion,    // Estado DESPUÉS (FINALIZADA con pago)
                event.getDescripcionDetallada(),
                event.getAdminEmail(),
                event.getAdminEmail(),  // Usar email como nombre si no hay nombre disponible
                event.getAdminRol(),
                event.getIpAddress()
            );
            
            logger.debug("✅ Auditoría de finalización registrada exitosamente - Reserva: {}", 
                    event.getReservaId());
            
        } catch (Exception e) {
            logger.error("❌ Error al registrar auditoría de finalización - Reserva: {}", 
                    event.getReservaId(), e);
        }
    }
}
