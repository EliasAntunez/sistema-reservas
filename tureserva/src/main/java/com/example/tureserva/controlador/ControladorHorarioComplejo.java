package com.example.tureserva.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.tureserva.servicio.ServicioAdministradorComplejo;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioHorarioComplejo;
import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.HorarioComplejo;
import com.example.tureserva.modelo.DiaSemana;
import com.example.tureserva.utiles.ManejadorMensajes;

import java.util.List;

@Controller
@RequestMapping("/admin-complejo/horarios")
public class ControladorHorarioComplejo {

    private final ServicioHorarioComplejo servicioHorarioComplejo;
    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;

    public ControladorHorarioComplejo(ServicioHorarioComplejo servicioHorarioComplejo,
                                    ServicioAdministradorComplejo servicioAdministradorComplejo,
                                    ServicioComplejoDeportivo servicioComplejoDeportivo) {
        this.servicioHorarioComplejo = servicioHorarioComplejo;
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
    }

    // ===== GESTIÓN DE HORARIOS =====

    /**
     * Mostrar horarios de un complejo específico
     */
    @GetMapping("/complejo/{complejoId}")
    public String verHorariosComplejo(@PathVariable Long complejoId, 
                                    Authentication authentication, 
                                    Model model) {
        try {
            // Verificar permisos del administrador
            if (!verificarPermisoComplejo(complejoId, authentication, model)) {
                return "redirect:/admin-complejo/mis-complejos";
            }

            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

            List<HorarioComplejo> horarios = servicioHorarioComplejo.obtenerHorariosPorComplejo(complejo);

            model.addAttribute("complejo", complejo);
            model.addAttribute("horarios", horarios);
            model.addAttribute("diasSemana", DiaSemana.values());

            return "admin-complejo/horarios/ver";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar los horarios: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }

    /**
     * Mostrar formulario para crear nuevo horario
     */
    @GetMapping("/complejo/{complejoId}/nuevo")
    public String mostrarFormularioNuevoHorario(@PathVariable Long complejoId,
                                              Authentication authentication,
                                              Model model) {
        try {
            // Verificar permisos del administrador
            if (!verificarPermisoComplejo(complejoId, authentication, model)) {
                return "redirect:/admin-complejo/mis-complejos";
            }

            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

            HorarioComplejo nuevoHorario = new HorarioComplejo();
            nuevoHorario.setComplejoDeportivo(complejo);

            model.addAttribute("complejo", complejo);
            model.addAttribute("horario", nuevoHorario);
            model.addAttribute("diasSemana", DiaSemana.values());

            return "admin-complejo/horarios/nuevo";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar el formulario: " + e.getMessage());
            return "redirect:/admin-complejo/horarios/complejo/" + complejoId;
        }
    }

    /**
     * Procesar creación de nuevo horario
     */
    @PostMapping("/complejo/{complejoId}/nuevo")
    public String crearHorario(@PathVariable Long complejoId,
                             @ModelAttribute("horario") HorarioComplejo horario,
                             BindingResult bindingResult,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        try {
            // Verificar permisos del administrador
            if (!verificarPermisoComplejo(complejoId, authentication, model)) {
                return "redirect:/admin-complejo/mis-complejos";
            }

            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

            // Establecer el complejo en el horario
            horario.setComplejoDeportivo(complejo);

            // Validar el horario
            if (!validarHorario(horario, bindingResult)) {
                model.addAttribute("complejo", complejo);
                model.addAttribute("diasSemana", DiaSemana.values());
                return "admin-complejo/horarios/nuevo";
            }

            // Guardar el horario (la validación de solapamiento se hace en el servicio)
            servicioHorarioComplejo.guardarHorario(horario);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Horario creado exitosamente");
            
            return "redirect:/admin-complejo/horarios/complejo/" + complejoId;

        } catch (RuntimeException e) {
            ManejadorMensajes.agregarMensajeError(model, e.getMessage());
            model.addAttribute("complejo", servicioComplejoDeportivo.obtenerPorId(complejoId).orElse(null));
            model.addAttribute("diasSemana", DiaSemana.values());
            return "admin-complejo/horarios/nuevo";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al crear el horario: " + e.getMessage());
            model.addAttribute("complejo", servicioComplejoDeportivo.obtenerPorId(complejoId).orElse(null));
            model.addAttribute("diasSemana", DiaSemana.values());
            return "admin-complejo/horarios/nuevo";
        }
    }

    /**
     * Mostrar formulario para editar horario existente
     */
    @GetMapping("/editar/{horarioId}")
    public String mostrarFormularioEditarHorario(@PathVariable Long horarioId,
                                               Authentication authentication,
                                               Model model) {
        try {
            HorarioComplejo horario = servicioHorarioComplejo.obtenerPorId(horarioId)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

            // Verificar permisos del administrador
            if (!verificarPermisoComplejo(horario.getComplejoDeportivo().getId_complejo(), authentication, model)) {
                return "redirect:/admin-complejo/mis-complejos";
            }

            model.addAttribute("horario", horario);
            model.addAttribute("complejo", horario.getComplejoDeportivo());
            model.addAttribute("diasSemana", DiaSemana.values());

            return "admin-complejo/horarios/editar";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar el horario: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }

    /**
     * Procesar actualización de horario
     */
    @PostMapping("/editar/{horarioId}")
    public String actualizarHorario(@PathVariable Long horarioId,
                                  @ModelAttribute("horario") HorarioComplejo horarioFormulario,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        HorarioComplejo horarioExistente = null;
        try {
            horarioExistente = servicioHorarioComplejo.obtenerPorId(horarioId)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

            // Verificar permisos del administrador
            if (!verificarPermisoComplejo(horarioExistente.getComplejoDeportivo().getId_complejo(), authentication, model)) {
                return "redirect:/admin-complejo/mis-complejos";
            }

            // Validar el horario
            if (!validarHorario(horarioFormulario, bindingResult)) {
                model.addAttribute("complejo", horarioExistente.getComplejoDeportivo());
                model.addAttribute("diasSemana", DiaSemana.values());
                return "admin-complejo/horarios/editar";
            }

            // Actualizar campos
            horarioExistente.setDiaSemana(horarioFormulario.getDiaSemana());
            horarioExistente.setHoraApertura(horarioFormulario.getHoraApertura());
            horarioExistente.setHoraCierre(horarioFormulario.getHoraCierre());

            servicioHorarioComplejo.actualizarHorario(horarioExistente);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Horario actualizado exitosamente");
            
            return "redirect:/admin-complejo/horarios/complejo/" + horarioExistente.getComplejoDeportivo().getId_complejo();

        } catch (RuntimeException e) {
            ManejadorMensajes.agregarMensajeError(model, e.getMessage());
            if (horarioExistente != null) {
                model.addAttribute("complejo", horarioExistente.getComplejoDeportivo());
            }
            model.addAttribute("diasSemana", DiaSemana.values());
            return "admin-complejo/horarios/editar";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al actualizar el horario: " + e.getMessage());
            return "admin-complejo/horarios/editar";
        }
    }

    /**
     * Eliminar horario
     */
    @PostMapping("/eliminar/{horarioId}")
    public String eliminarHorario(@PathVariable Long horarioId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            HorarioComplejo horario = servicioHorarioComplejo.obtenerPorId(horarioId)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

            Long complejoId = horario.getComplejoDeportivo().getId_complejo();

            // Verificar permisos del administrador (sin model, solo verificación)
            String emailUsuario = authentication.getName();
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            
            if (administrador == null || !horario.getComplejoDeportivo().getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para eliminar este horario");
                return "redirect:/admin-complejo/mis-complejos";
            }

            servicioHorarioComplejo.eliminarHorario(horarioId);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Horario eliminado exitosamente");
            
            return "redirect:/admin-complejo/horarios/complejo/" + complejoId;

        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al eliminar el horario: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }

    /**
     * Crear horarios por defecto para un complejo
     */
    @PostMapping("/complejo/{complejoId}/crear-defecto")
    public String crearHorariosDefecto(@PathVariable Long complejoId,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            // Verificar permisos del administrador (sin model, solo verificación)
            String emailUsuario = authentication.getName();
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            
            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

            if (administrador == null || !complejo.getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para gestionar este complejo");
                return "redirect:/admin-complejo/mis-complejos";
            }

            servicioHorarioComplejo.crearHorariosDefecto(complejo);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Horarios por defecto creados exitosamente");
            
            return "redirect:/admin-complejo/horarios/complejo/" + complejoId;

        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al crear horarios por defecto: " + e.getMessage());
            return "redirect:/admin-complejo/horarios/complejo/" + complejoId;
        }
    }

    // ===== MÉTODOS AUXILIARES =====

    /**
     * Verificar si el administrador tiene permisos sobre el complejo
     */
    private boolean verificarPermisoComplejo(Long complejoId, Authentication authentication, Model model) {
        try {
            String emailUsuario = authentication.getName();
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            
            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(model, "No se encontró el administrador de complejo");
                return false;
            }

            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));
            
            if (!complejo.getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(model, "No tiene permisos para gestionar este complejo");
                return false;
            }

            return true;
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, "Error al verificar permisos: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validar datos del horario
     */
    private boolean validarHorario(HorarioComplejo horario, BindingResult bindingResult) {
        boolean esValido = true;

        // Validar día de la semana
        if (horario.getDiaSemana() == null) {
            bindingResult.rejectValue("diaSemana", "error.horario", "Debe seleccionar un día de la semana");
            esValido = false;
        }

        // Validar hora de apertura
        if (horario.getHoraApertura() == null) {
            bindingResult.rejectValue("horaApertura", "error.horario", "Debe especificar la hora de apertura");
            esValido = false;
        }

        // Validar hora de cierre
        if (horario.getHoraCierre() == null) {
            bindingResult.rejectValue("horaCierre", "error.horario", "Debe especificar la hora de cierre");
            esValido = false;
        }

        // Validar que la hora de cierre sea posterior a la de apertura
/*
        if (horario.getHoraApertura() != null && horario.getHoraCierre() != null) {
            if (!horario.getHoraCierre().isAfter(horario.getHoraApertura())) {
                bindingResult.rejectValue("horaCierre", "error.horario", 
                    "La hora de cierre debe ser posterior a la hora de apertura");
                esValido = false;
            }
        }
*/
        return esValido;

    }
}
