package com.example.tureserva.controlador;

import com.example.tureserva.modelo.AuditoriaEvento;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.TipoEvento;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioAuditoria;
import com.example.tureserva.servicio.ServicioValidacionPermisos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Controlador para el módulo de auditoría de AdminComplejo.
 * Permite visualizar el historial de eventos de los complejos del administrador.
 */
@Controller
@RequestMapping("/admin-complejo/auditoria")
@Slf4j
public class ControladorAuditoria {
    
    private final ServicioAuditoria servicioAuditoria;
    private final ServicioValidacionPermisos servicioValidacionPermisos;
    private final RepositorioComplejoDeportivo repositorioComplejo;
    
    public ControladorAuditoria(ServicioAuditoria servicioAuditoria,
                                ServicioValidacionPermisos servicioValidacionPermisos,
                                RepositorioComplejoDeportivo repositorioComplejo) {
        this.servicioAuditoria = servicioAuditoria;
        this.servicioValidacionPermisos = servicioValidacionPermisos;
        this.repositorioComplejo = repositorioComplejo;
    }
    
    /**
     * Lista eventos de auditoría para un complejo específico.
     * 
     * @param complejoId ID del complejo a auditar
     * @param tipoEvento tipo de evento a filtrar (opcional)
     * @param desde fecha inicial (opcional)
     * @param hasta fecha final (opcional)
     * @param busqueda texto a buscar en descripción (opcional)
     * @param page número de página (default: 0)
     * @param size tamaño de página (default: 20)
     */
    @GetMapping("/{complejoId}")
    public String listarAuditoria(@PathVariable Long complejoId,
                                   @RequestParam(required = false) String tipoEvento,  // String para manejar "" correctamente
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                   @RequestParam(required = false) String busqueda,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   Authentication authentication,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            log.info("🔍 INICIO listarAuditoria - complejoId={}, tipoEvento='{}', desde={}, hasta={}, busqueda='{}', page={}", 
                     complejoId, tipoEvento, desde, hasta, busqueda, page);
            
            // Convertir string tipoEvento a enum (manejar "" y null correctamente)
            TipoEvento tipoEventoEnum = null;
            if (tipoEvento != null && !tipoEvento.trim().isEmpty()) {
                try {
                    tipoEventoEnum = TipoEvento.valueOf(tipoEvento);
                    log.debug("Tipo de evento parseado: {}", tipoEventoEnum);
                } catch (IllegalArgumentException e) {
                    log.warn("⚠️ Tipo de evento inválido: '{}', se ignorará", tipoEvento);
                }
            }
            
            // Validar que el AdminComplejo tenga acceso a este complejo
            log.debug("Validando permisos para usuario: {}", authentication.getName());
            servicioValidacionPermisos.validarPermisoSobreComplejoOThrow(authentication, complejoId);
            
            log.debug("Buscando complejo con ID: {}", complejoId);
            ComplejoDeportivo complejo = repositorioComplejo.findById(complejoId)
                    .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado"));
            
            // Convertir fechas a LocalDateTime (inicio y fin del día)
            LocalDateTime desdeDateTime = desde != null ? desde.atStartOfDay() : null;
            LocalDateTime hastaDateTime = hasta != null ? hasta.atTime(23, 59, 59) : null;
            log.debug("Fechas convertidas - desde: {}, hasta: {}", desdeDateTime, hastaDateTime);
            
            // Crear configuración de paginación
            Pageable pageable = PageRequest.of(page, size);
            log.debug("Paginación configurada - page: {}, size: {}", page, size);
            
            // Obtener eventos con filtros
            Page<AuditoriaEvento> eventos;
            if (tipoEventoEnum != null || desdeDateTime != null || hastaDateTime != null || 
                (busqueda != null && !busqueda.trim().isEmpty())) {
                // Búsqueda con filtros
                log.info("🔎 Aplicando filtros de búsqueda - tipoEvento={}, desde={}, hasta={}, busqueda='{}'",
                         tipoEventoEnum, desdeDateTime, hastaDateTime, busqueda);
                eventos = servicioAuditoria.buscarConFiltros(
                    complejoId, 
                    tipoEventoEnum, 
                    desdeDateTime, 
                    hastaDateTime, 
                    null, // usuarioEmail (no filtrar por usuario por ahora)
                    busqueda != null ? busqueda.trim() : null,
                    pageable
                );
                log.info("✅ Búsqueda con filtros completada - {} resultados encontrados", eventos.getTotalElements());
            } else {
                // Listado simple sin filtros
                log.debug("Obteniendo listado simple sin filtros");
                eventos = servicioAuditoria.obtenerEventosPorComplejo(complejoId, pageable);
                log.debug("Listado simple completado - {} resultados", eventos.getTotalElements());
            }
            
            // Agregar datos al modelo
            model.addAttribute("complejo", complejo);
            model.addAttribute("eventos", eventos);
            model.addAttribute("tiposEvento", TipoEvento.values());
            model.addAttribute("tipoEventoSeleccionado", tipoEventoEnum);  // Usar enum, no string
            model.addAttribute("desde", desde);
            model.addAttribute("hasta", hasta);
            model.addAttribute("busqueda", busqueda);
            model.addAttribute("paginaActual", page);
            model.addAttribute("totalPaginas", eventos.getTotalPages());
            model.addAttribute("totalElementos", eventos.getTotalElements());
            
            log.info("✅ ÉXITO listarAuditoria - Devolviendo vista con {} eventos (página {}/{})", 
                     eventos.getNumberOfElements(), page + 1, eventos.getTotalPages());
            return "admin-complejo/auditoria/listar";
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Intento de acceso no autorizado a auditoría del complejo {} por {}", 
                     complejoId, authentication.getName());
            redirectAttributes.addFlashAttribute("error", 
                "No tienes permiso para acceder a la auditoría de este complejo");
            return "redirect:/admin-complejo/dashboard";
        } catch (Exception e) {
            log.error("❌❌❌ ERROR CRÍTICO en listarAuditoria ❌❌❌");
            log.error("Complejo ID: {}", complejoId);
            log.error("Tipo Exception: {}", e.getClass().getName());
            log.error("Mensaje: {}", e.getMessage());
            log.error("Causa raíz: {}", e.getCause() != null ? e.getCause().getMessage() : "null");
            log.error("Stack trace completo:", e);
            
            // Log de parámetros para debugging
            log.error("Parámetros recibidos: tipoEvento='{}', desde={}, hasta={}, busqueda='{}'",
                     tipoEvento, desde, hasta, busqueda);
            
            redirectAttributes.addFlashAttribute("error", 
                "Error al cargar el historial de auditoría: " + e.getMessage());
            return "redirect:/admin-complejo/dashboard";
        }
    }
    
    /**
     * Muestra el historial de un recurso específico (cancha, salón, reserva, etc.).
     * 
     * @param complejoId ID del complejo
     * @param recursoTipo tipo de recurso (CANCHA, SALON, RESERVA, etc.)
     * @param recursoId ID del recurso
     */
    @GetMapping("/{complejoId}/recurso/{recursoTipo}/{recursoId}")
    public String historialRecurso(@PathVariable Long complejoId,
                                    @PathVariable String recursoTipo,
                                    @PathVariable Long recursoId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    Authentication authentication,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            // Validar acceso
            servicioValidacionPermisos.validarPermisoSobreComplejoOThrow(authentication, complejoId);
            
            ComplejoDeportivo complejo = repositorioComplejo.findById(complejoId)
                    .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado"));
            
            Pageable pageable = PageRequest.of(page, size);
            Page<AuditoriaEvento> eventos = servicioAuditoria.obtenerHistorialRecurso(
                complejoId, recursoTipo, recursoId, pageable
            );
            
            model.addAttribute("complejo", complejo);
            model.addAttribute("eventos", eventos);
            model.addAttribute("recursoTipo", recursoTipo);
            model.addAttribute("recursoId", recursoId);
            model.addAttribute("paginaActual", page);
            model.addAttribute("totalPaginas", eventos.getTotalPages());
            
            return "admin-complejo/auditoria/historial-recurso";
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Acceso no autorizado a historial de recurso");
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para ver este recurso");
            return "redirect:/admin-complejo/dashboard";
        } catch (Exception e) {
            log.error("❌ Error al cargar historial de recurso: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al cargar el historial");
            return "redirect:/admin-complejo/dashboard";
        }
    }
    
    /**
     * Obtiene estadísticas de auditoría para el dashboard.
     * 
     * @param complejoId ID del complejo
     */
    @GetMapping("/{complejoId}/estadisticas")
    @ResponseBody
    public java.util.Map<String, Long> obtenerEstadisticas(@PathVariable Long complejoId,
                                                            Authentication authentication) {
        try {
            // Validar acceso
            servicioValidacionPermisos.validarPermisoSobreComplejoOThrow(authentication, complejoId);
            
            // Contar eventos por tipo
            java.util.Map<String, Long> estadisticas = new java.util.HashMap<>();
            
            for (TipoEvento tipo : TipoEvento.values()) {
                long count = servicioAuditoria.contarEventosPorTipo(complejoId, tipo);
                if (count > 0) {
                    estadisticas.put(tipo.name(), count);
                }
            }
            
            return estadisticas;
            
        } catch (Exception e) {
            log.error("❌ Error al obtener estadísticas de auditoría: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }
    
    /**
     * Endpoint de diagnóstico para probar la query directamente
     */
    @GetMapping("/{complejoId}/test-query")
    @ResponseBody
    public java.util.Map<String, Object> testQuery(@PathVariable Long complejoId,
                                                     @RequestParam(required = false) String tipoEvento,
                                                     @RequestParam(required = false) String desde,
                                                     @RequestParam(required = false) String hasta,
                                                     Authentication authentication) {
        java.util.Map<String, Object> resultado = new java.util.HashMap<>();
        try {
            log.info("🧪 TEST QUERY - complejoId: {}, tipoEvento: '{}', desde: {}, hasta: {}",
                    complejoId, tipoEvento, desde, hasta);
            
            servicioValidacionPermisos.validarPermisoSobreComplejoOThrow(authentication, complejoId);
            
            LocalDateTime desdeDateTime = desde != null ? LocalDate.parse(desde).atStartOfDay() : null;
            LocalDateTime hastaDateTime = hasta != null ? LocalDate.parse(hasta).atTime(23, 59, 59) : null;
            TipoEvento tipoEventoEnum = (tipoEvento != null && !tipoEvento.trim().isEmpty()) 
                ? TipoEvento.valueOf(tipoEvento) : null;
            
            Pageable pageable = PageRequest.of(0, 5);
            Page<AuditoriaEvento> eventos = servicioAuditoria.buscarConFiltros(
                complejoId, tipoEventoEnum, desdeDateTime, hastaDateTime, null, null, pageable
            );
            
            resultado.put("success", true);
            resultado.put("totalElementos", eventos.getTotalElements());
            resultado.put("totalPaginas", eventos.getTotalPages());
            resultado.put("numeroResultados", eventos.getNumberOfElements());
            resultado.put("parametros", java.util.Map.of(
                "complejoId", complejoId,
                "tipoEvento", tipoEvento != null ? tipoEvento : "null",
                "desde", desdeDateTime != null ? desdeDateTime.toString() : "null",
                "hasta", hastaDateTime != null ? hastaDateTime.toString() : "null"
            ));
            
            log.info("✅ TEST QUERY exitoso: {} eventos encontrados", eventos.getTotalElements());
            
        } catch (Exception e) {
            log.error("❌ TEST QUERY falló: {}", e.getMessage(), e);
            resultado.put("success", false);
            resultado.put("error", e.getMessage());
            resultado.put("errorType", e.getClass().getName());
            if (e.getCause() != null) {
                resultado.put("cause", e.getCause().getMessage());
            }
        }
        return resultado;
    }
}
