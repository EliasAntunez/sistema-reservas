package com.example.tureserva.servicio;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;

@Service
public class ServicioEmail {
    private final Logger logger = LoggerFactory.getLogger(ServicioEmail.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username:no-reply@tureserva.example}")
    private String mailFrom;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ServicioEmail(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * Envía una alerta climática al cliente sobre su reserva.
     * Incluye 3 botones de acción: Mantener, Reprogramar, Cancelar.
     * 
        * @param reserva La reserva afectada
        * @param descripcionClima Descripción del clima pronosticado
        * @param probabilidadLluvia Probabilidad de precipitación (%)
        * @param tipoPrecipitacion Tipo de precipitación (puede ser null)
        * @param precipMmHora Precipitación estimada en mm/h (puede ser null)
     */
    @Async
        public void enviarAlertaClimatica(com.example.tureserva.modelo.Reserva reserva, 
                                  String descripcionClima, 
                                  Integer probabilidadLluvia,
                                  com.example.tureserva.servicio.dto.TipoPrecipitacion tipoPrecipitacion,
                                  Double precipMmHora) {
        try {
            String destinatario = reserva.getCliente().getEmail();
            
            if (destinatario == null || destinatario.isBlank()) {
                logger.warn("No se envía alerta climática: email vacío para reserva {}", reserva.getCodigoReserva());
                return;
            }
            
            // Obtener primer detalle para información del espacio
            com.example.tureserva.modelo.DetalleReserva primerDetalle = 
                !reserva.getDetalles().isEmpty() ? reserva.getDetalles().get(0) : null;
                
            String nombreEspacio = primerDetalle != null ? 
                primerDetalle.getEspacioReservable().getNombre() : "Espacio reservado";
            String nombreComplejo = primerDetalle != null ? 
                primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo() : "";
            
            Context ctx = new Context();
            String nombreCompleto = reserva.getCliente().getNombre() + " " + reserva.getCliente().getApellido();
            ctx.setVariable("nombreCliente", nombreCompleto);
            ctx.setVariable("codigoReserva", reserva.getCodigoReserva());
            ctx.setVariable("fecha", reserva.getFechaReserva());
            ctx.setVariable("hora", primerDetalle != null ? primerDetalle.getHoraInicio() : null);
            ctx.setVariable("nombreEspacio", nombreEspacio);
            ctx.setVariable("nombreComplejo", nombreComplejo);
            ctx.setVariable("descripcionClima", descripcionClima);
            ctx.setVariable("probabilidadLluvia", probabilidadLluvia);
            // Usar el método getEtiqueta() del enum para obtener la etiqueta legible
            String tipoLabel = tipoPrecipitacion != null ? tipoPrecipitacion.getEtiqueta() : null;

            ctx.setVariable("tipoPrecipitacion", tipoPrecipitacion);
            ctx.setVariable("tipoPrecipitacionLabel", tipoLabel);
            ctx.setVariable("precipMmHora", precipMmHora);
            ctx.setVariable("reservaId", reserva.getId());
            ctx.setVariable("baseUrl", baseUrl);
            
            String html = templateEngine.process("email/alerta-clima", ctx);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, 
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, 
                StandardCharsets.UTF_8.name());
                
            helper.setTo(destinatario);
            helper.setFrom(mailFrom);
            helper.setSubject("⚠️ Alerta Climática - Reserva " + reserva.getCodigoReserva());
            helper.setText(html, true);
            
            mailSender.send(message);
            logger.info("Alerta climática enviada a {} para reserva {}", destinatario, reserva.getCodigoReserva());
            
        } catch (MessagingException ex) {
            logger.error("Error enviando alerta climática para reserva {}: {}", 
                reserva.getCodigoReserva(), ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.error("Error inesperado al enviar alerta climática para reserva {}: {}", 
                reserva.getCodigoReserva(), ex.getMessage(), ex);
        }
    }

    /**
     * Versión compat: delega a la nueva firma sin tipo/precip.
     */
    @Async
    public void enviarAlertaClimatica(com.example.tureserva.modelo.Reserva reserva, String descripcionClima, Integer probabilidadLluvia) {
        enviarAlertaClimatica(reserva, descripcionClima, probabilidadLluvia, null, null);
    }

    /**
     * Envía el email de confirmación de reserva de forma asíncrona.
     * No lanza excepción hacia el controlador; en caso de error solo registra.
     */
    @Async
    public void enviarConfirmacionReserva(com.example.tureserva.servicio.dto.EmailReservaDTO dto, String destinatario) {
        try {
            if (destinatario == null || destinatario.isBlank()) {
                logger.warn("No se envía email: destinatario vacío para la reserva {}", dto != null ? dto.getCodigoReserva() : "<unknown>");
                return;
            }

            Context ctx = new Context();
            if (dto != null) {
                // Debug: registrar contenido del DTO para inspección antes de renderizar la plantilla
                if (logger.isDebugEnabled()) {
                    logger.debug("Email DTO - reserva: {} montoTotal: {} montoSenia: {} creditoAplicado: {} descuentoOfertaFlash: {} montoRestante: {} requirioSenia: {} detallesCount: {}",
                            dto.getCodigoReserva(), dto.getMontoTotal(), dto.getMontoSenia(), dto.getCreditoAplicado(), 
                            dto.getDescuentoOfertaFlash(), dto.getMontoRestante(), dto.isRequirioSenia(), dto.getDetalles() == null ? 0 : dto.getDetalles().size());
                    if (dto.getDetalles() != null) {
                        for (com.example.tureserva.servicio.dto.EmailDetalleDTO d : dto.getDetalles()) {
                            logger.debug(" - Detalle id={} espacio='{}' subtotal={} serviciosCount={}",
                                    d.getId(), d.getEspacioNombre(), d.getSubtotal(), d.getServicios() == null ? 0 : d.getServicios().size());
                            if (d.getServicios() != null) {
                                for (com.example.tureserva.servicio.dto.EmailServicioAdicionalDTO s : d.getServicios()) {
                                    logger.debug("   - Servicio id={} nombre='{}' cantidad={} subtotal={}", s.getId(), s.getNombre(), s.getCantidad(), s.getSubtotal());
                                }
                            }
                        }
                    }
                }
                ctx.setVariable("nombreCliente", dto.getNombreCliente());
                ctx.setVariable("codigoReserva", dto.getCodigoReserva());
                ctx.setVariable("fecha", dto.getFecha());
                ctx.setVariable("nombreComplejo", dto.getNombreComplejo());
                ctx.setVariable("montoTotal", dto.getMontoTotal());
                // Pasar subtotales y datos de seña
                ctx.setVariable("subtotalEspacios", dto.getSubtotalEspacios());
                ctx.setVariable("subtotalServicios", dto.getSubtotalServicios());
                ctx.setVariable("montoSenia", dto.getMontoSenia());
                ctx.setVariable("montoRestante", dto.getMontoRestante());
                ctx.setVariable("requirioSenia", dto.isRequirioSenia());
                ctx.setVariable("creditoAplicado", dto.getCreditoAplicado());
                ctx.setVariable("descuentoOfertaFlash", dto.getDescuentoOfertaFlash());
                // Pasar detalles completos y metadata para la plantilla
                ctx.setVariable("detalles", dto.getDetalles());
                ctx.setVariable("recordatorios", dto.getRecordatorios());
                ctx.setVariable("politicaCancelacionResumen", dto.getPoliticaCancelacionResumen());
            }
            
            // Agregar baseUrl para enlaces en el template
            ctx.setVariable("baseUrl", baseUrl);

            String html = templateEngine.process("email/confirmacion", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setTo(destinatario);
            helper.setFrom(mailFrom);
            helper.setSubject("Confirmación de reserva " + (dto != null ? dto.getCodigoReserva() : ""));
            helper.setText(html, true);

            mailSender.send(message);
            logger.info("Email de confirmación enviado a {} para reserva {}", destinatario, dto != null ? dto.getCodigoReserva() : "<unknown>");

        } catch (MessagingException ex) {
            logger.error("Error enviando email de confirmación para reserva {}: {}", dto != null ? dto.getCodigoReserva() : "<unknown>", ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.error("Error inesperado al enviar email de confirmación para reserva {}: {}", dto != null ? dto.getCodigoReserva() : "<unknown>", ex.getMessage(), ex);
        }
    }

    /**
     * Envía un email usando una plantilla Thymeleaf personalizada.
     * 
     * @param destinatario Email del destinatario
     * @param asunto Asunto del email
     * @param templateName Nombre de la plantilla (ej: "email/aviso-recupero-senia")
     * @param variables Mapa de variables para la plantilla
     */
    @Async
    public void enviarEmailConTemplate(String destinatario, String asunto, String templateName, java.util.Map<String, Object> variables) {
        try {
            if (destinatario == null || destinatario.isBlank()) {
                logger.warn("No se envía email: destinatario vacío para template {}", templateName);
                return;
            }

            Context ctx = new Context();
            if (variables != null) {
                variables.forEach(ctx::setVariable);
            }

            String html = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setTo(destinatario);
            helper.setFrom(mailFrom);
            helper.setSubject(asunto);
            helper.setText(html, true);

            mailSender.send(message);
            logger.info("Email enviado a {} usando template {}", destinatario, templateName);

        } catch (MessagingException ex) {
            logger.error("Error enviando email con template {} a {}: {}", templateName, destinatario, ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.error("Error inesperado al enviar email con template {} a {}: {}", templateName, destinatario, ex.getMessage(), ex);
        }
    }
}
