package com.example.tureserva.servicio;

import com.example.tureserva.modelo.DetalleReserva;
import com.example.tureserva.modelo.PoliticaCancelacion;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.repositorio.RepositorioReserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

/**
 * Servicio para enviar avisos proactivos sobre vencimiento de plazo de cancelación.
 * Ejecuta un proceso programado cada hora para detectar reservas que necesitan aviso.
 * 
 * <p>Flujo de negocio:
 * 1. Detecta reservas CONFIRMADA donde el plazo de cancelación gratuita ha vencido
 * 2. Envía email al cliente ofreciendo generar Oferta Flash si cancela ahora
 * 3. Marca la reserva con aviso_recupero_enviado = true
 * 
 * @author TuReserva
 */
@Service
public class ServicioAvisos {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicioAvisos.class);
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    private final RepositorioReserva repositorioReserva;
    private final ServicioEmail servicioEmail;
    
    public ServicioAvisos(
            RepositorioReserva repositorioReserva,
            ServicioEmail servicioEmail) {
        this.repositorioReserva = repositorioReserva;
        this.servicioEmail = servicioEmail;
    }
    
    /**
     * Proceso programado que se ejecuta cada hora para enviar avisos.
     * Cron: Cada hora en punto (minuto 0)
     */
    @Scheduled(cron = "0 0 * * * *") // Cada hora
    @Transactional
    public void procesarAvisosRecuperoSenia() {
        logger.info("🔔 Iniciando proceso de avisos de recupero de seña...");
        
        try {
            // Obtener todas las reservas que podrían necesitar aviso
            List<Reserva> reservasCandidatas = repositorioReserva.findReservasParaAvisoRecupero();
            
            if (reservasCandidatas.isEmpty()) {
                logger.info("No hay reservas que requieran aviso de recupero");
                return;
            }
            
            logger.info("Encontradas {} reservas candidatas para aviso", reservasCandidatas.size());
            
            int avisosEnviados = 0;
            int errores = 0;
            
            LocalDateTime ahora = LocalDateTime.now();
            
            for (Reserva reserva : reservasCandidatas) {
                try {
                    // Validar que la reserva tenga detalles
                    if (reserva.getDetalles() == null || reserva.getDetalles().isEmpty()) {
                        logger.warn("Reserva {} no tiene detalles, saltando", reserva.getId());
                        continue;
                    }
                    
                    // Obtener el primer detalle para verificar política y horario
                    DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                    
                    if (primerDetalle.getEspacioReservable() == null) {
                        logger.warn("Detalle de reserva {} no tiene espacio, saltando", reserva.getId());
                        continue;
                    }
                    
                    PoliticaCancelacion politica = primerDetalle.getEspacioReservable()
                        .getPoliticaCancelacion();
                    
                    if (politica == null) {
                        logger.debug("Reserva {} no tiene política de cancelación, saltando", 
                                reserva.getId());
                        continue;
                    }
                    
                    // Calcular el momento de inicio de la reserva
                    LocalDateTime inicioReserva = LocalDateTime.of(
                        primerDetalle.getFechaReserva() != null 
                            ? primerDetalle.getFechaReserva() 
                            : reserva.getFechaReserva(),
                        primerDetalle.getHoraInicio()
                    );
                    
                    // Calcular horas restantes hasta el inicio
                    long horasRestantes = ChronoUnit.HOURS.between(ahora, inicioReserva);
                    
                    // Verificar si el plazo de cancelación gratuita ha vencido
                    if (horasRestantes > 0 && 
                        horasRestantes <= politica.getHorasAnticipacionMinima()) {
                        
                        // Enviar aviso
                        enviarAvisoRecupero(reserva, politica, horasRestantes);
                        
                        // Marcar como enviado
                        reserva.setAvisoRecuperoEnviado(true);
                        repositorioReserva.save(reserva);
                        
                        avisosEnviados++;
                        
                        logger.info("✅ Aviso enviado a {} para reserva {} (faltan {} horas)", 
                                reserva.getCliente().getEmail(), 
                                reserva.getCodigoReserva(),
                                horasRestantes);
                    }
                    
                } catch (Exception e) {
                    errores++;
                    logger.error("Error procesando reserva {}: {}", 
                            reserva.getId(), e.getMessage(), e);
                }
            }
            
            logger.info("✅ Proceso completado: {} avisos enviados, {} errores", 
                    avisosEnviados, errores);
            
        } catch (Exception e) {
            logger.error("Error crítico en proceso de avisos: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Envía el email de aviso de vencimiento de plazo al cliente.
     */
    private void enviarAvisoRecupero(
            Reserva reserva, 
            PoliticaCancelacion politica, 
            long horasRestantes) {
        
        try {
            // Validar que la reserva tenga seña
            if (reserva.getMontoSenia() == null || reserva.getMontoSenia().compareTo(java.math.BigDecimal.ZERO) == 0) {
                logger.warn("Reserva {} no tiene seña configurada, omitiendo aviso de recupero", reserva.getId());
                return;
            }
            
            java.math.BigDecimal recuperoPosible = reserva.getMontoSenia()
                    .divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            
            // Obtener datos desde el primer detalle
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            String nombreComplejo = primerDetalle.getEspacioReservable() != null && 
                                   primerDetalle.getEspacioReservable().getComplejoDeportivo() != null
                                   ? primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo()
                                   : "Complejo";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("nombreCliente", reserva.getCliente().getNombre());
            variables.put("horasRestantes", horasRestantes);
            variables.put("nombreComplejo", nombreComplejo);
            variables.put("fecha", primerDetalle.getFechaReserva());
            variables.put("hora", primerDetalle.getHoraInicio());
            variables.put("montoTotal", reserva.getMontoTotal());
            variables.put("montoSenia", reserva.getMontoSenia());
            variables.put("montoRecupero", recuperoPosible);
            variables.put("urlReserva", baseUrl + "/reservas/mis-reservas");
            
            servicioEmail.enviarEmailConTemplate(
                reserva.getCliente().getEmail(),
                "⏰ Tu Reserva Está Por Expirar - Recupera el 50% de tu Seña",
                "email/aviso-recupero-senia",
                variables
            );
            
            logger.debug("Email de aviso enviado a {}", reserva.getCliente().getEmail());
            
        } catch (Exception e) {
            logger.error("Error al enviar email de aviso: {}", e.getMessage(), e);
            throw new RuntimeException("Error al enviar email de aviso", e);
        }
    }
    
    /**
     * Proceso manual para enviar avisos (útil para testing)
     */
    @Transactional
    public int enviarAvisosManuales() {
        logger.info("Ejecutando proceso manual de avisos...");
        procesarAvisosRecuperoSenia();
        return 0; // Retorna el número de avisos enviados
    }
}
