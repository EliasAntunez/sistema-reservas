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

    public ServicioEmail(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
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
                    logger.debug("Email DTO - reserva: {} montoTotal: {} detallesCount: {}",
                            dto.getCodigoReserva(), dto.getMontoTotal(), dto.getDetalles() == null ? 0 : dto.getDetalles().size());
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
                // Pasar detalles completos y metadata para la plantilla
                ctx.setVariable("detalles", dto.getDetalles());
                ctx.setVariable("recordatorios", dto.getRecordatorios());
                ctx.setVariable("politicaCancelacionResumen", dto.getPoliticaCancelacionResumen());
            }

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
}
