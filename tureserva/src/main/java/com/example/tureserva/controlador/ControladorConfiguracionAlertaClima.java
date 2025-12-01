package com.example.tureserva.controlador;

import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.repositorio.*;
import com.example.tureserva.servicio.ServicioEmail;
import com.example.tureserva.servicio.ServicioOpenMeteo;
import com.example.tureserva.servicio.dto.RespuestaClimaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Controlador para la configuración de alertas climáticas.
 * Permite a los administradores de complejo configurar cuándo y cómo recibir alertas.
 */
@Controller
@RequestMapping("/admin-complejo/alertas-clima")
public class ControladorConfiguracionAlertaClima {
    
    private static final Logger log = LoggerFactory.getLogger(ControladorConfiguracionAlertaClima.class);
    
    private final RepositorioAdministradorComplejo repositorioAdminComplejo;
    private final RepositorioConfiguracionAlertaClima repositorioConfigAlerta;
    private final RepositorioReserva repositorioReserva;
    private final ServicioOpenMeteo servicioClima;
    private final ServicioEmail servicioEmail;
    
    public ControladorConfiguracionAlertaClima(
            RepositorioAdministradorComplejo repositorioAdminComplejo,
            RepositorioConfiguracionAlertaClima repositorioConfigAlerta,
            RepositorioReserva repositorioReserva,
            ServicioOpenMeteo servicioClima,
            ServicioEmail servicioEmail) {
        this.repositorioAdminComplejo = repositorioAdminComplejo;
        this.repositorioConfigAlerta = repositorioConfigAlerta;
        this.repositorioReserva = repositorioReserva;
        this.servicioClima = servicioClima;
        this.servicioEmail = servicioEmail;
    }
    
    /**
     * Muestra el formulario de configuración de alertas climáticas.
     */
    @Transactional
    @GetMapping("/configurar")
    public String mostrarConfiguracion(
            Authentication auth, 
            @RequestParam(value = "complejoId", required = false) Long complejoId,
            Model model, 
            RedirectAttributes redirectAttributes) {
        
        AdministradorComplejo admin = obtenerAdminAutenticado(auth);
        
        if (admin == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar el administrador");
            return "redirect:/admin-complejo/dashboard";
        }
        
        // Si el admin no tiene complejos
        if (admin.getComplejosDeportivos() == null || admin.getComplejosDeportivos().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No tienes complejos asignados");
            return "redirect:/admin-complejo/dashboard";
        }
        
        // Determinar qué complejo configurar
        ComplejoDeportivo complejo;
        if (complejoId != null) {
            complejo = admin.getComplejosDeportivos().stream()
                .filter(c -> c.getId_complejo().equals(complejoId))
                .findFirst()
                .orElse(null);
            
            if (complejo == null) {
                redirectAttributes.addFlashAttribute("error", "Complejo no encontrado");
                return "redirect:/admin-complejo/mis-complejos";
            }
        } else {
            // Tomar el primer complejo por defecto
            complejo = admin.getComplejosDeportivos().get(0);
        }
        
        // Verificar que el complejo tenga coordenadas
        if (complejo.getLatitud() == null || complejo.getLongitud() == null) {
            redirectAttributes.addFlashAttribute("error", 
                "Tu complejo no tiene coordenadas configuradas. Por favor, actualiza la ubicación primero.");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        // Buscar configuración existente
        ConfiguracionAlertaClima config = repositorioConfigAlerta.findByComplejo(complejo)
            .orElse(null);
        
        model.addAttribute("complejo", complejo);
        model.addAttribute("configuracion", config);
        model.addAttribute("tieneConfiguracion", config != null);
        model.addAttribute("complejos", admin.getComplejosDeportivos()); // Lista de todos los complejos
        
        return "admin-complejo/alertas-clima/configurar";
    }
    
    /**
     * Guarda la configuración de alertas climáticas.
     */
    @Transactional
    @PostMapping("/guardar")
    public String guardarConfiguracion(
            Authentication auth,
            @RequestParam("complejoId") Long complejoId,
            @RequestParam("estrategia") String estrategiaStr,
            @RequestParam(value = "horasAnticipacion", required = false) Integer horasAnticipacion,
            @RequestParam(value = "horarioFijo", required = false) String horarioFijoStr,
            @RequestParam(value = "umbralProbabilidad", defaultValue = "50") Integer umbralProbabilidad,
            @RequestParam(value = "umbralLlovizna", required = false) Integer umbralLlovizna,
            @RequestParam(value = "umbralLluvia", required = false) Integer umbralLluvia,
            @RequestParam(value = "umbralChubascos", required = false) Integer umbralChubascos,
            @RequestParam(value = "umbralTormenta", required = false) Integer umbralTormenta,
            @RequestParam(value = "umbralNieve", required = false) Integer umbralNieve,
            @RequestParam(value = "umbralOtro", required = false) Integer umbralOtro,
            @RequestParam(value = "activo", defaultValue = "false") Boolean activo,
            RedirectAttributes redirectAttributes) {
        
        AdministradorComplejo admin = obtenerAdminAutenticado(auth);
        
        if (admin == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar el administrador");
            return "redirect:/admin-complejo/dashboard";
        }
        
        // Buscar el complejo
        ComplejoDeportivo complejo = admin.getComplejosDeportivos().stream()
            .filter(c -> c.getId_complejo().equals(complejoId))
            .findFirst()
            .orElse(null);
        
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        try {
            EstrategiaAlerta estrategia = EstrategiaAlerta.valueOf(estrategiaStr);
            
            // Buscar o crear configuración
            ConfiguracionAlertaClima config = repositorioConfigAlerta.findByComplejo(complejo)
                .orElse(new ConfiguracionAlertaClima());
            
            config.setComplejo(complejo);
            config.setEstrategia(estrategia);
            config.setUmbralProbabilidad(umbralProbabilidad);
            // Umbrales por tipo (opcionales)
            config.setUmbralLlovizna(umbralLlovizna);
            config.setUmbralLluvia(umbralLluvia);
            config.setUmbralChubascos(umbralChubascos);
            config.setUmbralTormenta(umbralTormenta);
            config.setUmbralNieve(umbralNieve);
            config.setUmbralOtro(umbralOtro);
            config.setActivo(activo);
            
            // Configurar según estrategia
            if (estrategia == EstrategiaAlerta.HORAS_ANTES) {
                config.setHorasAnticipacion(horasAnticipacion);
                config.setHorarioFijo(null);
            } else if (estrategia == EstrategiaAlerta.HORARIO_FIJO) {
                config.setHorasAnticipacion(null);
                LocalTime horarioFijo = LocalTime.parse(horarioFijoStr);
                config.setHorarioFijo(horarioFijo);
            }
            
            repositorioConfigAlerta.save(config);
            
            log.info("Configuración de alertas guardada para complejo {}: estrategia={}, activo={}", 
                    complejo.getNombre_complejo(), estrategia, activo);
            
            redirectAttributes.addFlashAttribute("success", 
                "Configuración guardada exitosamente. " + 
                (activo ? "Las alertas climáticas están activas." : "Las alertas están desactivadas."));
            
        } catch (Exception e) {
            log.error("Error al guardar configuración de alertas: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Error al guardar la configuración: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/alertas-clima/configurar?complejoId=" + complejoId;
    }
    
    /**
     * ENDPOINT DE PRUEBA: Envía alertas climáticas a TODAS las reservas confirmadas
     * del complejo, sin importar la fecha ni la configuración.
     * 
     * ⚠️ SOLO PARA DEMOSTRACIÓN - No usar en producción
     */
    @Transactional
    @PostMapping("/avisar-ahora")
    public String avisarAhora(
            Authentication auth,
            @RequestParam("complejoId") Long complejoId,
            RedirectAttributes redirectAttributes) {
        
        AdministradorComplejo admin = obtenerAdminAutenticado(auth);
        
        if (admin == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar el administrador");
            return "redirect:/admin-complejo/dashboard";
        }
        
        // Buscar el complejo
        ComplejoDeportivo complejo = admin.getComplejosDeportivos().stream()
            .filter(c -> c.getId_complejo().equals(complejoId))
            .findFirst()
            .orElse(null);
        
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        // Validar coordenadas
        if (complejo.getLatitud() == null || complejo.getLongitud() == null) {
            redirectAttributes.addFlashAttribute("error", 
                "El complejo no tiene coordenadas configuradas");
            return "redirect:/admin-complejo/alertas-clima/configurar";
        }
        
        try {
            log.info("🧪 MODO PRUEBA: Iniciando envío de alertas para complejo {}", 
                    complejo.getNombre_complejo());
            
            // Obtener configuración para el umbral
            ConfiguracionAlertaClima config = repositorioConfigAlerta.findByComplejo(complejo)
                .orElse(null);
            
            int umbral = (config != null) ? config.getUmbralProbabilidad() : 50;
            
            // Buscar TODAS las reservas confirmadas del complejo
            List<Reserva> reservasConfirmadas = repositorioReserva.findByComplejoDeportivoIdAndEstado(
                complejo.getId_complejo(),
                EstadoReserva.CONFIRMADA
            );
            
            if (reservasConfirmadas.isEmpty()) {
                redirectAttributes.addFlashAttribute("warning", 
                    "No hay reservas confirmadas para notificar");
                return "redirect:/admin-complejo/alertas-clima/configurar";
            }
            
            int enviados = 0;
            int errores = 0;
            
            for (Reserva reserva : reservasConfirmadas) {
                try {
                    // Obtener primer detalle para la fecha/hora
                    if (reserva.getDetalles().isEmpty()) {
                        log.warn("Reserva {} no tiene detalles, saltando", reserva.getId());
                        continue;
                    }
                    
                    DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                    LocalDateTime fechaHoraReserva = LocalDateTime.of(
                        reserva.getFechaReserva(),
                        primerDetalle.getHoraInicio()
                    );
                    
                    // Consultar clima real
                    RespuestaClimaDTO clima = servicioClima.consultarClima(
                        complejo.getLatitud(),
                        complejo.getLongitud(),
                        fechaHoraReserva,
                        umbral
                    );

                    // Construir umbrales por tipo (usar la misma lógica que el scheduler)
                    int fallback = (config != null && config.getUmbralProbabilidad() != null) ? config.getUmbralProbabilidad() : 50;
                    java.util.Map<com.example.tureserva.servicio.dto.TipoPrecipitacion, Integer> umbralesPorTipo = new java.util.HashMap<>();
                    umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.LLOVIZNA, config != null && config.getUmbralLlovizna() != null ? config.getUmbralLlovizna() : fallback);
                    umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.LLUVIA, config != null && config.getUmbralLluvia() != null ? config.getUmbralLluvia() : fallback);
                    umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.CHAPARRON, config != null && config.getUmbralChubascos() != null ? config.getUmbralChubascos() : fallback);
                    umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.TORMENTA, config != null && config.getUmbralTormenta() != null ? config.getUmbralTormenta() : fallback);
                    umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.NIEVE, config != null && config.getUmbralNieve() != null ? config.getUmbralNieve() : fallback);
                    umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO, config != null && config.getUmbralOtro() != null ? config.getUmbralOtro() : fallback);

                    Integer probabilidad = clima.getProbabilidadPrecipitacion() != null ? clima.getProbabilidadPrecipitacion() : 0;
                    com.example.tureserva.servicio.dto.TipoPrecipitacion tipo = clima.getTipoPrecipitacion() != null ? clima.getTipoPrecipitacion() : com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO;
                    int umbralTipo = umbralesPorTipo.getOrDefault(tipo, fallback);

                    // Solo enviar si cumple el umbral por tipo
                    if (probabilidad >= umbralTipo) {
                        String descripcion = clima.isHayMalClima() ? clima.getDescripcion() : "Condiciones: " + clima.getDescripcion();
                        servicioEmail.enviarAlertaClimatica(
                            reserva,
                            descripcion,
                            probabilidad,
                            clima.getTipoPrecipitacion(),
                            clima.getPrecipMmHora()
                        );
                        enviados++;
                        log.info("✅ Alerta de prueba enviada a reserva {} ({}): tipo={}, prob={}%, umbral={}%", 
                                reserva.getCodigoReserva(), reserva.getCliente().getEmail(), tipo, probabilidad, umbralTipo);
                    } else {
                        log.info("⏭️ Se omitió alerta para reserva {}: probabilidad {}% < umbral {}% (tipo={})", 
                                reserva.getId(), probabilidad, umbralTipo, tipo);
                    }
                    
                } catch (Exception e) {
                    errores++;
                    log.error("Error enviando alerta a reserva {}: {}", 
                            reserva.getId(), e.getMessage());
                }
            }
            
            String mensaje = String.format(
                "✅ Proceso completado: %d alertas enviadas, %d errores. " +
                "Revisa los emails de los clientes con reservas confirmadas.",
                enviados, errores
            );
            
            redirectAttributes.addFlashAttribute("success", mensaje);
            
            log.info("🧪 MODO PRUEBA: Finalizado - {} enviados, {} errores", enviados, errores);
            
        } catch (Exception e) {
            log.error("Error en envío masivo de alertas: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Error al enviar alertas: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/alertas-clima/configurar?complejoId=" + complejoId;
    }
    
    /**
     * ENDPOINT DE PRUEBA: Consulta el clima para los próximos 3 días
     * y muestra las predicciones para encontrar días con alta probabilidad de lluvia.
     */
    @Transactional
    @GetMapping("/pronostico-semanal")
    @ResponseBody
    public String consultarPronosticoSemanal(
            Authentication auth,
            @RequestParam(value = "complejoId", required = false) Long complejoId) {
        
        try {
            AdministradorComplejo admin = obtenerAdminAutenticado(auth);
            
            if (admin == null) {
                return generarPaginaError("No se pudo identificar el administrador");
            }
            
            // Determinar complejo
            ComplejoDeportivo complejo;
            if (complejoId != null) {
                complejo = admin.getComplejosDeportivos().stream()
                    .filter(c -> c.getId_complejo().equals(complejoId))
                    .findFirst()
                    .orElse(null);
            } else {
                complejo = admin.getComplejosDeportivos().isEmpty() ? null : admin.getComplejosDeportivos().get(0);
            }
            
            if (complejo == null) {
                return generarPaginaError("No se encontró el complejo");
            }
            
            if (complejo.getLatitud() == null || complejo.getLongitud() == null) {
                return generarPaginaError("El complejo no tiene coordenadas configuradas");
            }
            
            // Obtener configuración de umbrales
            ConfiguracionAlertaClima config = repositorioConfigAlerta.findByComplejo(complejo).orElse(null);
            int fallback = (config != null && config.getUmbralProbabilidad() != null) ? config.getUmbralProbabilidad() : 50;
            
            StringBuilder resultado = new StringBuilder();
            resultado.append("<html><head><meta charset='UTF-8'><title>Pronóstico 3 Días</title>");
            resultado.append("<style>body{font-family:Arial;padding:20px;background:#f4f4f4;} ");
            resultado.append(".container{max-width:1200px;margin:0 auto;background:white;padding:20px;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);} ");
            resultado.append("table{border-collapse:collapse;width:100%;margin-top:20px;} ");
            resultado.append("th,td{border:1px solid #ddd;padding:12px;text-align:left;} th{background-color:#3b82f6;color:white;} ");
            resultado.append(".alta{background-color:#fef3c7;} .muy-alta{background-color:#fee2e2;font-weight:bold;} ");
            resultado.append(".info{background:#eff6ff;padding:15px;border-left:4px solid #3b82f6;margin:20px 0;border-radius:4px;} ");
            resultado.append("a{color:#3b82f6;text-decoration:none;font-weight:600;} a:hover{text-decoration:underline;}</style></head><body>");
            resultado.append("<div class='container'>");
            resultado.append("<h1>🌦️ Pronóstico 3 Días - ").append(complejo.getNombre_complejo()).append("</h1>");
            resultado.append("<p>📍 Ubicación: Lat ").append(String.format("%.4f", complejo.getLatitud())).append(", Lon ").append(String.format("%.4f", complejo.getLongitud())).append("</p>");
            resultado.append("<div class='info'><strong>💡 Umbrales configurados:</strong> ");
            if (config != null) {
                resultado.append("General: ").append(fallback).append("%");
                if (config.getUmbralLluvia() != null) resultado.append(" | Lluvia: ").append(config.getUmbralLluvia()).append("%");
                if (config.getUmbralChubascos() != null) resultado.append(" | Chaparrón: ").append(config.getUmbralChubascos()).append("%");
                if (config.getUmbralTormenta() != null) resultado.append(" | Tormenta: ").append(config.getUmbralTormenta()).append("%");
            } else {
                resultado.append("Sin configuración (usando 50% por defecto)");
            }
            resultado.append("</div>");
            resultado.append("<table><tr><th>Fecha</th><th>Hora</th><th>Condiciones</th><th>Tipo</th><th>Probabilidad</th><th>¿Alerta?</th><th>Temp (°C)</th></tr>");
            
            LocalDateTime ahora = LocalDateTime.now();
            int consultasExitosas = 0;
            int consultasFallidas = 0;
            
            // Consultar clima para los próximos 3 días (límite de API gratuita), a las 10:00, 14:00, 18:00 y 20:00
            for (int dia = 0; dia < 3; dia++) {
                for (int hora : new int[]{10, 14, 18, 20}) {
                    LocalDateTime fechaHora = ahora.plusDays(dia).withHour(hora).withMinute(0).withSecond(0);
                    
                    try {
                        RespuestaClimaDTO clima = servicioClima.consultarClima(
                            complejo.getLatitud(),
                            complejo.getLongitud(),
                            fechaHora,
                            fallback
                        );
                        
                        Integer prob = clima.getProbabilidadPrecipitacion() != null ? clima.getProbabilidadPrecipitacion() : 0;
                        com.example.tureserva.servicio.dto.TipoPrecipitacion tipo = clima.getTipoPrecipitacion() != null ? 
                            clima.getTipoPrecipitacion() : com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO;
                        
                        // Determinar umbral según tipo
                        int umbralTipo = fallback;
                        if (config != null) {
                            switch (tipo) {
                                case LLOVIZNA: umbralTipo = config.getUmbralLlovizna() != null ? config.getUmbralLlovizna() : fallback; break;
                                case LLUVIA: umbralTipo = config.getUmbralLluvia() != null ? config.getUmbralLluvia() : fallback; break;
                                case CHAPARRON: umbralTipo = config.getUmbralChubascos() != null ? config.getUmbralChubascos() : fallback; break;
                                case TORMENTA: umbralTipo = config.getUmbralTormenta() != null ? config.getUmbralTormenta() : fallback; break;
                                case NIEVE: umbralTipo = config.getUmbralNieve() != null ? config.getUmbralNieve() : fallback; break;
                                default: umbralTipo = config.getUmbralOtro() != null ? config.getUmbralOtro() : fallback;
                            }
                        }
                        
                        boolean enviariaAlerta = prob >= umbralTipo;
                        String rowClass = enviariaAlerta ? (prob >= 70 ? "muy-alta" : "alta") : "";
                        
                        resultado.append("<tr class='").append(rowClass).append("'>");
                        resultado.append("<td>").append(fechaHora.toLocalDate()).append("</td>");
                        resultado.append("<td>").append(fechaHora.toLocalTime()).append("</td>");
                        resultado.append("<td>").append(clima.getDescripcion()).append("</td>");
                        resultado.append("<td>").append(tipo).append("</td>");
                        resultado.append("<td><strong>").append(prob).append("%</strong> (umbral: ").append(umbralTipo).append("%)</td>");
                        resultado.append("<td>").append(enviariaAlerta ? "✅ SÍ" : "❌ NO").append("</td>");
                        resultado.append("<td>").append(clima.getTemperatura() != null ? String.format("%.1f", clima.getTemperatura()) : "N/A").append("</td>");
                        resultado.append("</tr>");
                        
                        consultasExitosas++;
                        
                    } catch (Exception e) {
                        consultasFallidas++;
                        log.error("Error consultando clima para {}: {}", fechaHora, e.getMessage());
                        resultado.append("<tr><td>").append(fechaHora.toLocalDate()).append("</td>");
                        resultado.append("<td>").append(fechaHora.toLocalTime()).append("</td>");
                        resultado.append("<td colspan='5' style='color:#ef4444;'>❌ Error: ").append(e.getMessage()).append("</td></tr>");
                    }
                }
            }
            
            resultado.append("</table>");
            resultado.append("<div class='info' style='margin-top:20px;'>");
            resultado.append("<strong>📊 Resumen:</strong> ").append(consultasExitosas).append(" consultas exitosas, ")
                     .append(consultasFallidas).append(" fallidas<br>");
            resultado.append("<strong>💡 Consejo:</strong> Reserva en las fechas/horas marcadas en amarillo o rojo para probar las alertas. ");
            resultado.append("Las filas en <span style='background:#fee2e2;padding:2px 6px;'>rojo</span> tienen probabilidad ≥70% y ");
            resultado.append("las <span style='background:#fef3c7;padding:2px 6px;'>amarillas</span> superan su umbral configurado.");
            resultado.append("</div>");
            resultado.append("<p style='margin-top:20px;'><a href='/admin-complejo/alertas-clima/configurar?complejoId=")
                     .append(complejo.getId_complejo()).append("'>← Volver a Configuración de Alertas</a></p>");
            resultado.append("</div></body></html>");
            
            return resultado.toString();
            
        } catch (Exception e) {
            log.error("Error general en pronostico-semanal: {}", e.getMessage(), e);
            return generarPaginaError("Error interno del servidor: " + e.getMessage());
        }
    }
    
    private String generarPaginaError(String mensaje) {
        return "<html><head><meta charset='UTF-8'><title>Error</title>" +
               "<style>body{font-family:Arial;padding:40px;text-align:center;background:#fee2e2;} " +
               ".error{background:white;padding:30px;border-radius:8px;display:inline-block;border:2px solid #ef4444;}</style></head>" +
               "<body><div class='error'><h1>❌ Error</h1><p>" + mensaje + "</p>" +
               "<p><a href='/admin-complejo/alertas-clima/configurar'>← Volver</a></p></div></body></html>";
    }
    
    /**
     * Obtiene el administrador autenticado.
     */
    private AdministradorComplejo obtenerAdminAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        
        String email = auth.getName();
        return repositorioAdminComplejo.findByEmail(email).orElse(null);
    }
}
