package com.example.tureserva.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.tureserva.servicio.ServicioSuperAdministrador;
import com.example.tureserva.modelo.SuperAdministrador;

@Controller
@RequestMapping("/super-admin")
public class ControladorSuperAdministrador {

    private final ServicioSuperAdministrador servicioSuperAdministrador;

    public ControladorSuperAdministrador(ServicioSuperAdministrador servicioSuperAdministrador) {
        this.servicioSuperAdministrador = servicioSuperAdministrador;
    }

    // ===== DASHBOARD SUPER ADMINISTRADOR =====

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        SuperAdministrador superAdmin = servicioSuperAdministrador.obtenerSuperAdministradorActivoPorEmail(email);
        
        if (superAdmin == null) {
            model.addAttribute("error", "No se pudo cargar el perfil del Super Administrador");
            return "redirect:/login";
        }

        // Estadísticas básicas para el dashboard
        long totalSuperAdmins = servicioSuperAdministrador.contarSuperAdministradoresActivos();

        model.addAttribute("superAdmin", superAdmin);
        model.addAttribute("totalSuperAdmins", totalSuperAdmins);

        return "super-admin/dashboard";
    }

    // ===== PERFIL DEL SUPER ADMINISTRADOR =====

    @GetMapping("/perfil")
    public String verPerfil(Authentication authentication, Model model) {
        String email = authentication.getName();
        SuperAdministrador superAdmin = servicioSuperAdministrador.obtenerSuperAdministradorActivoPorEmail(email);
        
        if (superAdmin == null) {
            model.addAttribute("error", "No se pudo cargar el perfil");
            return "redirect:/super-admin/dashboard";
        }
        
        model.addAttribute("superAdmin", superAdmin);
        return "super-admin/perfil/ver";
    }

    @GetMapping("/perfil/editar")
    public String mostrarFormularioEditarPerfil(Authentication authentication, Model model) {
        String email = authentication.getName();
        SuperAdministrador superAdmin = servicioSuperAdministrador.obtenerSuperAdministradorActivoPorEmail(email);
        
        if (superAdmin == null) {
            model.addAttribute("error", "No se pudo cargar el perfil");
            return "redirect:/super-admin/dashboard";
        }
        
        // Limpiar contraseña para no mostrarla
        superAdmin.setContrasena("");
        model.addAttribute("superAdmin", superAdmin);
        return "super-admin/perfil/editar";
    }

    @PostMapping("/perfil/editar")
    public String actualizarPerfil(@ModelAttribute("superAdmin") SuperAdministrador superAdminFormulario,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        
        String emailActual = authentication.getName();
        SuperAdministrador existente = servicioSuperAdministrador.obtenerSuperAdministradorActivoPorEmail(emailActual);
        
        if (existente == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo encontrar el perfil");
            return "redirect:/super-admin/perfil";
        }

        // Validaciones básicas
        if (superAdminFormulario.getNombre() == null || superAdminFormulario.getNombre().trim().isEmpty()) {
            bindingResult.rejectValue("nombre", "error.superAdmin", "El nombre es obligatorio");
        }
        if (superAdminFormulario.getApellido() == null || superAdminFormulario.getApellido().trim().isEmpty()) {
            bindingResult.rejectValue("apellido", "error.superAdmin", "El apellido es obligatorio");
        }
        if (superAdminFormulario.getEmail() == null || superAdminFormulario.getEmail().trim().isEmpty()) {
            bindingResult.rejectValue("email", "error.superAdmin", "El email es obligatorio");
        }

        // Verificar si el email cambió y si ya existe
        if (!superAdminFormulario.getEmail().equals(emailActual) && 
            servicioSuperAdministrador.verificarEmail(superAdminFormulario.getEmail())) {
            bindingResult.rejectValue("email", "error.superAdmin", "El email ya está en uso");
        }



        if (bindingResult.hasErrors()) {
            return "super-admin/perfil/editar";
        }

        try {
            // Actualizar datos
            existente.setNombre(superAdminFormulario.getNombre());
            existente.setApellido(superAdminFormulario.getApellido());
            existente.setEmail(superAdminFormulario.getEmail());

            servicioSuperAdministrador.actualizarSuperAdministrador(existente);
            redirectAttributes.addFlashAttribute("mensaje", "Perfil actualizado correctamente");
            return "redirect:/super-admin/perfil";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error al actualizar el perfil. Inténtelo de nuevo.");
            return "super-admin/perfil/editar";
        }
    }

    @GetMapping("/perfil/cambiar-contrasena")
    public String mostrarFormularioCambiarContrasena() {
        return "super-admin/perfil/cambiar-contrasena";
    }

    @PostMapping("/perfil/cambiar-contrasena")
    public String cambiarContrasena(@RequestParam("contrasenaActual") String contrasenaActual,
                                   @RequestParam("nuevaContrasena") String nuevaContrasena,
                                   @RequestParam("confirmarContrasena") String confirmarContrasena,
                                   Authentication authentication,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        
        String email = authentication.getName();
        SuperAdministrador superAdmin = servicioSuperAdministrador.obtenerSuperAdministradorActivoPorEmail(email);
        
        if (superAdmin == null) {
            model.addAttribute("error", "No se pudo encontrar el usuario");
            return "super-admin/perfil/cambiar-contrasena";
        }

        // Validaciones
        if (contrasenaActual == null || contrasenaActual.trim().isEmpty()) {
            model.addAttribute("error", "La contraseña actual es obligatoria");
            return "super-admin/perfil/cambiar-contrasena";
        }

        if (nuevaContrasena == null || nuevaContrasena.trim().isEmpty()) {
            model.addAttribute("error", "La nueva contraseña es obligatoria");
            return "super-admin/perfil/cambiar-contrasena";
        }
        
        if (nuevaContrasena.length() < 6) {
            model.addAttribute("error", "La nueva contraseña debe tener al menos 6 caracteres");
            return "super-admin/perfil/cambiar-contrasena";
        }
        
        if (!nuevaContrasena.equals(confirmarContrasena)) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            return "super-admin/perfil/cambiar-contrasena";
        }

        try {
            boolean actualizado = servicioSuperAdministrador.cambiarContrasena(superAdmin.getId(), contrasenaActual, nuevaContrasena);
            
            if (actualizado) {
                redirectAttributes.addFlashAttribute("mensaje", "Contraseña actualizada correctamente");
                return "redirect:/super-admin/perfil";
            } else {
                model.addAttribute("error", "La contraseña actual no es correcta");
                return "super-admin/perfil/cambiar-contrasena";
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Error al cambiar la contraseña. Inténtelo de nuevo.");
            return "super-admin/perfil/cambiar-contrasena";
        }
    }
}