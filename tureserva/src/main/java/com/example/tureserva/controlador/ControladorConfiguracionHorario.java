package com.example.tureserva.controlador;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.ConfiguracionHorario;
import com.example.tureserva.modelo.RangoHorario;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioConfiguracionHorario;
import com.example.tureserva.servicio.ServicioRangoHorario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Controlador para gestión de ConfiguracionHorario y RangoHorario
 * URL base: /admin-complejo/complejos/{complejoId}/horarios
 */
@Controller
@RequestMapping("/admin-complejo/complejos/{complejoId}/horarios")
@PreAuthorize("hasRole('ADMINISTRADOR_COMPLEJO')")
@RequiredArgsConstructor
@Slf4j
public class ControladorConfiguracionHorario {
    
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;
    private final ServicioConfiguracionHorario servicioConfiguracionHorario;
    private final ServicioRangoHorario servicioRangoHorario;
    
    /**
     * Lista todas las configuraciones de horario de un complejo
     * GET /admin-complejo/complejos/{complejoId}/horarios
     */
    @GetMapping
    @Transactional(readOnly = true)
    public String listar(
            @PathVariable Long complejoId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Listando configuraciones de horario para complejo ID: {}", complejoId);
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            log.warn("Complejo no encontrado o sin acceso: {}", complejoId);
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        log.debug("Complejo encontrado: {}", complejo.getNombre_complejo());
        
        List<ConfiguracionHorario> configuraciones = servicioConfiguracionHorario.listarPorComplejoConRangos(complejo);
        log.debug("Configuraciones encontradas: {}", configuraciones.size());
        
        model.addAttribute("complejo", complejo);
        model.addAttribute("configuraciones", configuraciones);
        model.addAttribute("configuracionMaster", complejo.getConfiguracionHorarioMaster());
        
        return "admin-complejo/horarios/listar";
    }
    
    /**
     * Muestra formulario para crear nueva configuración
     * GET /admin-complejo/complejos/{complejoId}/horarios/crear
     */
    @GetMapping("/crear")
    public String mostrarFormularioCrear(
            @PathVariable Long complejoId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        // No establecer el complejo aquí, se hará en el POST
        if (!model.containsAttribute("configuracion")) {
            model.addAttribute("configuracion", new ConfiguracionHorario());
        }
        
        model.addAttribute("complejo", complejo);
        
        return "admin-complejo/horarios/form-crear";
    }
    
    /**
     * Procesa la creación de nueva configuración
     * POST /admin-complejo/complejos/{complejoId}/horarios/crear
     */
    @PostMapping("/crear")
    public String crear(
            @PathVariable Long complejoId,
            @RequestParam("nombre") String nombre,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Creando configuración - Complejo: {}, Nombre: '{}'", complejoId, nombre);
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            log.warn("Complejo no encontrado: {}", complejoId);
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        // Validaciones manuales
        if (nombre == null || nombre.trim().isEmpty()) {
            log.warn("Intento de crear configuración sin nombre");
            ConfiguracionHorario configuracion = new ConfiguracionHorario();
            configuracion.setNombre(nombre);
            configuracion.setDescripcion(descripcion);
            model.addAttribute("configuracion", configuracion);
            model.addAttribute("complejo", complejo);
            model.addAttribute("error", "El nombre es obligatorio");
            return "admin-complejo/horarios/form-crear";
        }
        
        if (nombre.length() > 100) {
            log.warn("Nombre excede longitud máxima: {}", nombre.length());
            ConfiguracionHorario configuracion = new ConfiguracionHorario();
            configuracion.setNombre(nombre);
            configuracion.setDescripcion(descripcion);
            model.addAttribute("configuracion", configuracion);
            model.addAttribute("complejo", complejo);
            model.addAttribute("error", "El nombre no puede exceder 100 caracteres");
            return "admin-complejo/horarios/form-crear";
        }
        
        try {
            ConfiguracionHorario configuracion = new ConfiguracionHorario();
            configuracion.setNombre(nombre.trim());
            configuracion.setDescripcion(descripcion != null && !descripcion.trim().isEmpty() ? descripcion.trim() : null);
            configuracion.setComplejoDeportivo(complejo);
            
            ConfiguracionHorario nuevaConfig = servicioConfiguracionHorario.crear(configuracion);
            log.info("Configuración creada exitosamente - ID: {}, Nombre: '{}'", nuevaConfig.getId(), nuevaConfig.getNombre());
            
            redirectAttributes.addFlashAttribute("exito", 
                "Configuración '" + nuevaConfig.getNombre() + "' creada exitosamente");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + nuevaConfig.getId();
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al crear configuración: {}", e.getMessage());
            ConfiguracionHorario configuracion = new ConfiguracionHorario();
            configuracion.setNombre(nombre);
            configuracion.setDescripcion(descripcion);
            model.addAttribute("configuracion", configuracion);
            model.addAttribute("complejo", complejo);
            model.addAttribute("error", e.getMessage());
            return "admin-complejo/horarios/form-crear";
        } catch (Exception e) {
            log.error("Error inesperado al crear configuración", e);
            ConfiguracionHorario configuracion = new ConfiguracionHorario();
            configuracion.setNombre(nombre);
            configuracion.setDescripcion(descripcion);
            model.addAttribute("configuracion", configuracion);
            model.addAttribute("complejo", complejo);
            model.addAttribute("error", "Error inesperado: " + e.getMessage());
            return "admin-complejo/horarios/form-crear";
        }
    }
    
    /**
     * Muestra vista para gestionar rangos de una configuración
     * GET /admin-complejo/complejos/{complejoId}/horarios/gestionar/{configId}
     */
    @GetMapping("/gestionar/{configId}")
    public String gestionarRangos(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Gestionando rangos - Complejo: {}, Config: {}", complejoId, configId);
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            log.warn("Complejo no encontrado: {}", complejoId);
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        ConfiguracionHorario configuracion = servicioConfiguracionHorario.obtenerPorIdConRangos(configId)
            .orElse(null);
        
        if (configuracion == null || !configuracion.getComplejoDeportivo().getId_complejo().equals(complejoId)) {
            log.warn("Configuración no encontrada o no pertenece al complejo - Config: {}, Complejo: {}", configId, complejoId);
            redirectAttributes.addFlashAttribute("error", "Configuración no encontrada");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
        }
        
        log.debug("Configuración encontrada: '{}' con {} rangos", configuracion.getNombre(), configuracion.getRangosHorario().size());
        
        model.addAttribute("complejo", complejo);
        model.addAttribute("configuracion", configuracion);
        model.addAttribute("rangos", configuracion.getRangosHorario());
        model.addAttribute("diasSemana", DayOfWeek.values());
        
        return "admin-complejo/horarios/gestionar-rangos";
    }
    
    /**
     * Agrega un rango horario a una configuración
     * POST /admin-complejo/complejos/{complejoId}/horarios/gestionar/{configId}/agregar-rango
     */
    @PostMapping("/gestionar/{configId}/agregar-rango")
    public String agregarRango(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            @RequestParam("diaSemana") String diaSemanaStr,
            @RequestParam("horaApertura") String horaAperturaStr,
            @RequestParam("horaCierre") String horaCierreStr,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Agregando rango - Complejo: {}, Config: {}, Día: {}, Apertura: {}, Cierre: {}", 
                  complejoId, configId, diaSemanaStr, horaAperturaStr, horaCierreStr);
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            log.warn("Complejo no encontrado: {}", complejoId);
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        ConfiguracionHorario configuracion = servicioConfiguracionHorario.obtenerPorIdConRangos(configId)
            .orElse(null);
        
        if (configuracion == null || !configuracion.getComplejoDeportivo().getId_complejo().equals(complejoId)) {
            log.warn("Configuración no encontrada - Config: {}, Complejo: {}", configId, complejoId);
            redirectAttributes.addFlashAttribute("error", "Configuración no encontrada");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
        }
        
        try {
            // Validar campos obligatorios
            if (diaSemanaStr == null || diaSemanaStr.trim().isEmpty()) {
                log.warn("Día de semana vacío");
                redirectAttributes.addFlashAttribute("error", "El día de la semana es obligatorio");
                return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
            }
            
            if (horaAperturaStr == null || horaAperturaStr.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "La hora de apertura es obligatoria");
                return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
            }
            
            if (horaCierreStr == null || horaCierreStr.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "La hora de cierre es obligatoria");
                return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
            }
            
            // Crear el rango manualmente
            RangoHorario nuevoRango = new RangoHorario();
            nuevoRango.setDiaSemana(DayOfWeek.valueOf(diaSemanaStr));
            nuevoRango.setHoraApertura(java.time.LocalTime.parse(horaAperturaStr));
            nuevoRango.setHoraCierre(java.time.LocalTime.parse(horaCierreStr));
            nuevoRango.setConfiguracionHorario(configuracion);
            
            servicioRangoHorario.crear(nuevoRango);
            log.info("Rango agregado exitosamente - Config: {}, Día: {}, {}-{}", 
                     configId, diaSemanaStr, horaAperturaStr, horaCierreStr);
            redirectAttributes.addFlashAttribute("exito", "Rango horario agregado exitosamente");
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al agregar rango: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al agregar rango", e);
            redirectAttributes.addFlashAttribute("error", "Error al crear el rango: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
    }
    
    /**
     * Actualiza un rango horario
     * POST /admin-complejo/complejos/{complejoId}/horarios/gestionar/{configId}/actualizar-rango/{rangoId}
     */
    @PostMapping("/gestionar/{configId}/actualizar-rango/{rangoId}")
    public String actualizarRango(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            @PathVariable Long rangoId,
            @RequestParam String horaApertura,
            @RequestParam String horaCierre,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        RangoHorario rango = servicioRangoHorario.obtenerPorId(rangoId).orElse(null);
        
        if (rango == null || !rango.getConfiguracionHorario().getId().equals(configId)) {
            redirectAttributes.addFlashAttribute("error", "Rango no encontrado");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
        }
        
        try {
            rango.setHoraApertura(java.time.LocalTime.parse(horaApertura));
            rango.setHoraCierre(java.time.LocalTime.parse(horaCierre));
            servicioRangoHorario.actualizar(rango);
            redirectAttributes.addFlashAttribute("exito", "Rango actualizado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
    }
    
    /**
     * Elimina un rango horario
     * POST /admin-complejo/complejos/{complejoId}/horarios/gestionar/{configId}/eliminar-rango/{rangoId}
     */
    @PostMapping("/gestionar/{configId}/eliminar-rango/{rangoId}")
    public String eliminarRango(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            @PathVariable Long rangoId,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        try {
            servicioRangoHorario.eliminar(rangoId);
            redirectAttributes.addFlashAttribute("exito", "Rango eliminado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios/gestionar/" + configId;
    }
    
    /**
     * Asigna una configuración como horario Master del complejo
     * POST /admin-complejo/complejos/{complejoId}/horarios/asignar-master/{configId}
     */
    @PostMapping("/asignar-master/{configId}")
    public String asignarMaster(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        ConfiguracionHorario configuracion = servicioConfiguracionHorario.obtenerPorId(configId)
            .orElse(null);
        
        if (configuracion == null || !configuracion.getComplejoDeportivo().getId_complejo().equals(complejoId)) {
            redirectAttributes.addFlashAttribute("error", "Configuración no encontrada");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
        }
        
        complejo.setConfiguracionHorarioMaster(configuracion);
        servicioComplejoDeportivo.guardar(complejo);
        
        redirectAttributes.addFlashAttribute("exito", 
            "Configuración '" + configuracion.getNombre() + "' asignada como Horario Por Defecto del complejo");
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
    }
    
    /**
     * Asigna una configuración como horario Override para Canchas
     * POST /admin-complejo/complejos/{complejoId}/horarios/asignar-canchas/{configId}
     */
    @PostMapping("/asignar-canchas/{configId}")
    public String asignarCanchas(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        ConfiguracionHorario configuracion = servicioConfiguracionHorario.obtenerPorId(configId)
            .orElse(null);
        
        if (configuracion == null || !configuracion.getComplejoDeportivo().getId_complejo().equals(complejoId)) {
            redirectAttributes.addFlashAttribute("error", "Configuración no encontrada");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
        }
        
        complejo.setConfiguracionHorarioCanchas(configuracion);
        servicioComplejoDeportivo.guardar(complejo);
        
        redirectAttributes.addFlashAttribute("exito", 
            "Configuración '" + configuracion.getNombre() + "' asignada como Horario de Canchas");
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
    }
    
    /**
     * Asigna una configuración como horario Override para Salones
     * POST /admin-complejo/complejos/{complejoId}/horarios/asignar-salones/{configId}
     */
    @PostMapping("/asignar-salones/{configId}")
    public String asignarSalones(
            @PathVariable Long complejoId,
            @PathVariable Long configId,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        ConfiguracionHorario configuracion = servicioConfiguracionHorario.obtenerPorId(configId)
            .orElse(null);
        
        if (configuracion == null || !configuracion.getComplejoDeportivo().getId_complejo().equals(complejoId)) {
            redirectAttributes.addFlashAttribute("error", "Configuración no encontrada");
            return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
        }
        
        complejo.setConfiguracionHorarioSalones(configuracion);
        servicioComplejoDeportivo.guardar(complejo);
        
        redirectAttributes.addFlashAttribute("exito", 
            "Configuración '" + configuracion.getNombre() + "' asignada como Horario de Salones");
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
    }
    
    /**
     * Remueve la asignación de horario override (vuelve a usar Master)
     * POST /admin-complejo/complejos/{complejoId}/horarios/remover-override/{tipo}
     */
    @PostMapping("/remover-override/{tipo}")
    public String removerOverride(
            @PathVariable Long complejoId,
            @PathVariable String tipo,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        switch(tipo.toLowerCase()) {
            case "canchas":
                complejo.setConfiguracionHorarioCanchas(null);
                redirectAttributes.addFlashAttribute("exito", "Horario override de Canchas removido");
                break;
            case "salones":
                complejo.setConfiguracionHorarioSalones(null);
                redirectAttributes.addFlashAttribute("exito", "Horario override de Salones removido");
                break;
            default:
                redirectAttributes.addFlashAttribute("error", "Tipo inválido");
        }
        
        servicioComplejoDeportivo.guardar(complejo);
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
    }
    
    /**
     * Muestra información de uso de una configuración (para confirmar eliminación)
     * GET /admin-complejo/complejos/{complejoId}/horarios/{id}/info-uso
     */
    @GetMapping("/{id}/info-uso")
    @ResponseBody
    public java.util.Map<String, Object> obtenerInfoUso(
            @PathVariable Long complejoId,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            return java.util.Map.of("error", "Complejo no encontrado");
        }
        
        ConfiguracionHorario config = servicioConfiguracionHorario.obtenerPorId(id).orElse(null);
        if (config == null) {
            return java.util.Map.of("error", "Configuración no encontrada");
        }
        
        List<String> usos = servicioConfiguracionHorario.obtenerUsos(config);
        boolean puedeEliminar = usos.isEmpty();
        
        return java.util.Map.of(
            "puedeEliminar", puedeEliminar,
            "usos", usos,
            "nombre", config.getNombre()
        );
    }
    
    /**
     * Elimina lógicamente una configuración
     * POST /admin-complejo/complejos/{complejoId}/horarios/{id}/eliminar
     */
    @PostMapping("/{id}/eliminar")
    public String eliminarLogicamente(
            @PathVariable Long complejoId,
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false, defaultValue = "false") boolean desvincularAutomaticamente,
            RedirectAttributes redirectAttributes) {
        
        ComplejoDeportivo complejo = verificarAccesoComplejo(complejoId);
        if (complejo == null) {
            redirectAttributes.addFlashAttribute("error", "Complejo no encontrado o sin acceso");
            return "redirect:/admin-complejo/mis-complejos";
        }
        
        try {
            servicioConfiguracionHorario.eliminarLogicamente(id, motivo, desvincularAutomaticamente);
            redirectAttributes.addFlashAttribute("exito", "Configuración eliminada correctamente");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/complejos/" + complejoId + "/horarios";
    }
    
    /**
     * Verifica que el administrador tenga acceso al complejo
     */
    private ComplejoDeportivo verificarAccesoComplejo(Long complejoId) {
        //  Implementar verificación de seguridad real
        // Por ahora solo buscar el complejo
        return servicioComplejoDeportivo.obtenerPorId(complejoId).orElse(null);
    }
}
