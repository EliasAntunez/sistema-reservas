package com.example.tureserva.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.tureserva.servicio.ServicioAdministradorComplejo;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Usuario;
import com.example.tureserva.utiles.ValidadorFormulario;
import com.example.tureserva.utiles.ManejadorMensajes;
import com.example.tureserva.utiles.ValidadorContrasena;

import java.util.List;

@Controller
@RequestMapping("/admin-complejo")
public class ControladorAdministradorComplejo {

    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;

    public ControladorAdministradorComplejo(ServicioAdministradorComplejo servicioAdministradorComplejo,
                                          ServicioComplejoDeportivo servicioComplejoDeportivo) {
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
    }

    // ===== DASHBOARD ADMINISTRADOR COMPLEJO =====

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        Usuario usuario = (Usuario) adminComplejo;
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, "No se pudo cargar el perfil del Administrador de Complejo");
            return "redirect:/login";
        }
        model.addAttribute("usuario", usuario);
        model.addAttribute("adminComplejo", adminComplejo);

        return "admin-complejo/dashboard";
    }

    // ===== PERFIL DEL ADMINISTRADOR COMPLEJO =====

    @GetMapping("/perfil")
    public String verPerfil(Authentication authentication, Model model) {
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/admin-complejo/dashboard";
        }
        
        model.addAttribute("adminComplejo", adminComplejo);
        return "admin-complejo/perfil/ver";
    }

    @GetMapping("/perfil/editar")
    public String mostrarFormularioEditarPerfil(Authentication authentication, Model model) {
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/admin-complejo/dashboard";
        }
        
        // Limpiar contraseña para no mostrarla
        adminComplejo.setContrasena("");
        model.addAttribute("adminComplejo", adminComplejo);
        return "admin-complejo/perfil/editar";
    }

    @PostMapping("/perfil/editar")
    public String actualizarPerfil(@ModelAttribute("adminComplejo") AdministradorComplejo adminFormulario,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        
        String emailActual = authentication.getName();
        AdministradorComplejo existente = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailActual);
        
        if (existente == null) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/admin-complejo/perfil";
        }

        // Validaciones completas usando utilidad (incluyendo DNI)
        ValidadorFormulario.validarCamposCompletosUsuario(
            adminFormulario.getNombre(), 
            adminFormulario.getApellido(), 
            adminFormulario.getEmail(), 
            adminFormulario.getDni(),
            bindingResult
        );

        // Verificar si el email cambió y si ya existe
        if (!adminFormulario.getEmail().equals(emailActual) && 
            servicioAdministradorComplejo.verificarEmail(adminFormulario.getEmail())) {
            bindingResult.rejectValue("email", "error.adminComplejo", ManejadorMensajes.EMAIL_EN_USO);
        }

        // Verificar si el DNI cambió y ya existe
        if (adminFormulario.getDni() != null && !adminFormulario.getDni().trim().isEmpty()) {
            String dniExistente = existente.getDni() != null ? existente.getDni() : "";
            if (!adminFormulario.getDni().equals(dniExistente) && servicioAdministradorComplejo.verificarDni(adminFormulario.getDni())) {
                bindingResult.rejectValue("dni", "error.adminComplejo", "El DNI ya está registrado");
            }
        }

        if (bindingResult.hasErrors()) {
            return "admin-complejo/perfil/editar";
        }

        try {
            // Actualizar datos
            existente.setNombre(adminFormulario.getNombre());
            existente.setApellido(adminFormulario.getApellido());
            existente.setEmail(adminFormulario.getEmail());
            existente.setDni(adminFormulario.getDni());

            servicioAdministradorComplejo.actualizarAdministrador(existente);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.PERFIL_ACTUALIZADO);
            return "redirect:/admin-complejo/perfil";
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
            return "admin-complejo/perfil/editar";
        }
    }

    @GetMapping("/perfil/cambiar-contrasena")
    public String mostrarFormularioCambiarContrasena() {
        return "admin-complejo/perfil/cambiar-contrasena";
    }

    @PostMapping("/perfil/cambiar-contrasena")
    public String cambiarContrasena(@RequestParam("contrasenaActual") String contrasenaActual,
                                   @RequestParam("nuevaContrasena") String nuevaContrasena,
                                   @RequestParam("confirmarContrasena") String confirmarContrasena,
                                   Authentication authentication,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.USUARIO_NO_ENCONTRADO);
            return "admin-complejo/perfil/cambiar-contrasena";
        }

        // Usar validador unificado de contraseñas
        String errorValidacion = ValidadorContrasena.validarCambioContrasena(
            contrasenaActual, nuevaContrasena, confirmarContrasena, 
            model, null, "admin-complejo/perfil/cambiar-contrasena"
        );
        
        if (errorValidacion != null) {
            return errorValidacion;
        }

        try {
            boolean actualizado = servicioAdministradorComplejo.cambiarContrasena(adminComplejo.getId(), contrasenaActual, nuevaContrasena);
            
            if (actualizado) {
                ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.CONTRASENA_ACTUALIZADA);
                return "redirect:/admin-complejo/perfil";
            } else {
                ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.CONTRASENA_ACTUAL_INCORRECTA);
                return "admin-complejo/perfil/cambiar-contrasena";
            }
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
            return "admin-complejo/perfil/cambiar-contrasena";
        }
    }

    // ===== GESTIÓN DE COMPLEJOS =====

    /**
     * Redirigir de /admin-complejo a /admin-complejo/mis-complejos
     */
    @GetMapping("")
    public String redirectToMisComplejos() {
        return "redirect:/admin-complejo/mis-complejos";
    }

    /**
     * Mostrar los complejos del administrador logueado
     */
    @GetMapping("/mis-complejos")
    public String misComplejos(Model model, Authentication authentication) {
        try {
            // Obtener el email del usuario autenticado
            String emailUsuario = authentication.getName();
            
            // Buscar el administrador por email
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(model, "No se encontró el administrador de complejo");
                return "admin-complejo/mis-complejos";
            }

            // Obtener los complejos del administrador
            List<ComplejoDeportivo> misComplejos = servicioComplejoDeportivo.obtenerComplejosPorAdministradorYActivoTrue(administrador.getId());
            
            System.out.println("DEBUG: Administrador ID: " + administrador.getId() + 
                             ", Email: " + emailUsuario + 
                             ", Complejos encontrados: " + misComplejos.size());

            model.addAttribute("administrador", administrador);
            model.addAttribute("complejos", misComplejos);
            
            return "admin-complejo/mis-complejos";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar sus complejos: " + e.getMessage());
            return "admin-complejo/mis-complejos";
        }
    }

    /**
     * Gestionar complejo específico (proximamente)
     */
    @GetMapping("/gestionar/{id}")
    public String gestionarComplejo(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            // Obtener el email del usuario autenticado
            String emailUsuario = authentication.getName();
            
            // Buscar el administrador por email
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(model, "No se encontró el administrador de complejo");
                return "redirect:/admin-complejo/mis-complejos";
            }

            // Verificar que el complejo pertenece al administrador
            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(id)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));
            
            if (!complejo.getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(model, "No tiene permisos para gestionar este complejo");
                return "redirect:/admin-complejo/mis-complejos";
            }

            model.addAttribute("complejo", complejo);
            
            return "admin-complejo/gestionar";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar el complejo: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }
}
