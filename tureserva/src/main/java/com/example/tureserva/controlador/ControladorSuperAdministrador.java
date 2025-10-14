package com.example.tureserva.controlador;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.tureserva.servicio.ServicioAdministradorComplejo;
import com.example.tureserva.servicio.ServicioSuperAdministrador;
import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.SuperAdministrador;
import com.example.tureserva.utiles.ValidadorFormulario;
import com.example.tureserva.utiles.ManejadorMensajes;

@Controller
@RequestMapping("/super-admin")
public class ControladorSuperAdministrador {

    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    private final ServicioSuperAdministrador servicioSuperAdministrador;

    public ControladorSuperAdministrador(ServicioAdministradorComplejo servicioAdministradorComplejo,
                                        ServicioSuperAdministrador servicioSuperAdministrador) {
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
        this.servicioSuperAdministrador = servicioSuperAdministrador;
    }

    // ===== DASHBOARD SUPER ADMINISTRADOR =====

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        SuperAdministrador superAdmin = servicioSuperAdministrador.obtenerSuperAdministradorActivoPorEmail(email);
        
        if (superAdmin == null) {
            ManejadorMensajes.agregarMensajeError(model, "No se pudo cargar el perfil del Super Administrador");
            return "redirect:/login";
        }

        // Estadísticas básicas para el dashboard
        long totalSuperAdmins = servicioSuperAdministrador.contarSuperAdministradoresActivos();
        long totalAdministradoresComplejo = servicioAdministradorComplejo.contarAdministradoresActivos();

        model.addAttribute("superAdmin", superAdmin);
        model.addAttribute("totalSuperAdmins", totalSuperAdmins);
        model.addAttribute("totalAdministradoresComplejo", totalAdministradoresComplejo);

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
            ManejadorMensajes.agregarMensajeError(redirectAttributes, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/super-admin/perfil";
        }

        // Validaciones completas usando utilidad (incluyendo DNI)
        ValidadorFormulario.validarCamposCompletosUsuario(
            superAdminFormulario.getNombre(), 
            superAdminFormulario.getApellido(), 
            superAdminFormulario.getEmail(), 
            superAdminFormulario.getDni(),
            bindingResult // Agregar bindingResult para capturar errores
        );

        // Verificar si el email cambió y si ya existe
        if (!superAdminFormulario.getEmail().equals(emailActual) && 
            servicioSuperAdministrador.verificarEmail(superAdminFormulario.getEmail())) {
            bindingResult.rejectValue("email", "error.superAdmin", ManejadorMensajes.EMAIL_EN_USO);
        }

        // Verificar si el DNI cambió y ya existe
        if (!superAdminFormulario.getDni().equals(existente.getDni()) && 
            servicioSuperAdministrador.verificarDni(superAdminFormulario.getDni())) {
            bindingResult.rejectValue("dni", "error.superAdmin", "El DNI ya está registrado");
        }

        if (bindingResult.hasErrors()) {
            return "super-admin/perfil/editar";
        }

        try {
            // Actualizar datos
            existente.setNombre(superAdminFormulario.getNombre());
            existente.setApellido(superAdminFormulario.getApellido());
            existente.setEmail(superAdminFormulario.getEmail());
            existente.setDni(superAdminFormulario.getDni());

            servicioSuperAdministrador.actualizarSuperAdministrador(existente);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.PERFIL_ACTUALIZADO);
            return "redirect:/super-admin/perfil";
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
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

    // ===== GESTIÓN DE ADMINISTRADORES DE COMPLEJO =====

    @GetMapping("/administradores-complejo")
    public String listarAdministradoresComplejo(Model model) {
        List<AdministradorComplejo> administradores = servicioAdministradorComplejo.obtenerTodosLosAdministradores();
        model.addAttribute("administradores", administradores);
        return "super-admin/administradores-complejo/listar";
    }

    @GetMapping("/administradores-complejo/nuevo")
    public String mostrarFormularioNuevoAdministrador(Model model) {
        model.addAttribute("administradorComplejo", new AdministradorComplejo());
        return "super-admin/administradores-complejo/formulario";
    }

    @PostMapping("/administradores-complejo/guardar")
    public String guardarAdministradorComplejo(@ModelAttribute("administradorComplejo") AdministradorComplejo admin,
                                             BindingResult bindingResult,
                                             Model model,
                                             RedirectAttributes redirectAttributes) {
        
        // Validaciones completas usando utilidades (incluyendo DNI)
        ValidadorFormulario.validarCamposCompletosUsuario(
            admin.getNombre(), 
            admin.getApellido(), 
            admin.getEmail(), 
            admin.getDni(),
            bindingResult
        );
        
        ValidadorFormulario.validarContrasena(admin.getContrasena(), bindingResult, "contrasena");

        // Verificar si el email ya existe
        if (servicioAdministradorComplejo.verificarEmail(admin.getEmail())) {
            bindingResult.rejectValue("email", "error.administradorComplejo", ManejadorMensajes.EMAIL_EN_USO);
        }

        // Verificar si el DNI ya existe (si se proporcionó)
        if (admin.getDni() != null && !admin.getDni().trim().isEmpty() && 
            servicioAdministradorComplejo.verificarDni(admin.getDni())) {
            bindingResult.rejectValue("dni", "error.administradorComplejo", "El DNI ya está registrado");
        }

        if (bindingResult.hasErrors()) {
            return "super-admin/administradores-complejo/formulario";
        }

        try {
            servicioAdministradorComplejo.guardarAdministrador(admin);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Administrador de Complejo creado exitosamente");
            return "redirect:/super-admin/administradores-complejo";
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
            return "super-admin/administradores-complejo/formulario";
        }
    }

    @GetMapping("/administradores-complejo/editar/{id}")
    public String mostrarFormularioEditarAdministrador(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorId(id);
        
        if (admin == null) {
            redirectAttributes.addFlashAttribute("error", "Administrador no encontrado");
            return "redirect:/super-admin/administradores-complejo";
        }
        
        // Limpiar contraseña para no mostrarla
        admin.setContrasena("");
        model.addAttribute("administradorComplejo", admin);
        return "super-admin/administradores-complejo/formulario";
    }

    @PostMapping("/administradores-complejo/actualizar")
    public String actualizarAdministradorComplejo(@ModelAttribute("administradorComplejo") AdministradorComplejo admin,
                                                BindingResult bindingResult,
                                                Model model,
                                                RedirectAttributes redirectAttributes) {
        
        // Validaciones completas usando utilidades (incluyendo DNI)
        ValidadorFormulario.validarCamposCompletosUsuario(
            admin.getNombre(), 
            admin.getApellido(), 
            admin.getEmail(), 
            admin.getDni(),
            bindingResult
        );

        // Verificar si el email cambió y ya existe
        AdministradorComplejo existente = servicioAdministradorComplejo.obtenerAdministradorPorId(admin.getId());
        if (existente != null && !admin.getEmail().equals(existente.getEmail()) && 
            servicioAdministradorComplejo.verificarEmail(admin.getEmail())) {
            bindingResult.rejectValue("email", "error.administradorComplejo", "El email ya está en uso");
        }

        // Verificar si el DNI cambió y ya existe
        if (existente != null && admin.getDni() != null && !admin.getDni().trim().isEmpty()) {
            String dniExistente = existente.getDni() != null ? existente.getDni() : "";
            if (!admin.getDni().equals(dniExistente) && servicioAdministradorComplejo.verificarDni(admin.getDni())) {
                bindingResult.rejectValue("dni", "error.administradorComplejo", "El DNI ya está registrado");
            }
        }

        if (bindingResult.hasErrors()) {
            return "super-admin/administradores-complejo/formulario";
        }

        try {
            servicioAdministradorComplejo.actualizarAdministrador(admin);
            redirectAttributes.addFlashAttribute("mensaje", "Administrador actualizado exitosamente");
            return "redirect:/super-admin/administradores-complejo";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error al actualizar el administrador. Inténtelo de nuevo.");
            return "super-admin/administradores-complejo/formulario";
        }
    }

    @GetMapping("/administradores-complejo/ver/{id}")
    public String verAdministradorComplejo(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorId(id);
        
        if (admin == null) {
            redirectAttributes.addFlashAttribute("error", "Administrador no encontrado");
            return "redirect:/super-admin/administradores-complejo";
        }
        
        model.addAttribute("administradorComplejo", admin);
        return "super-admin/administradores-complejo/ver";
    }

    @PostMapping("/administradores-complejo/cambiar-estado/{id}")
    public String cambiarEstadoAdministrador(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorId(id);
            if (admin != null) {
                admin.setActivo(!admin.isActivo());
                servicioAdministradorComplejo.actualizarAdministrador(admin);
                redirectAttributes.addFlashAttribute("mensaje", "Estado del administrador actualizado exitosamente");
            } else {
                redirectAttributes.addFlashAttribute("error", "Administrador no encontrado");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cambiar el estado del administrador");
        }
        
        return "redirect:/super-admin/administradores-complejo";
    }
}