package com.example.tureserva.controlador;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioAdministradorComplejo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/super-admin/complejos")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControladorComplejoDeportivo {

    private final ServicioComplejoDeportivo servicioComplejo;
    private final ServicioAdministradorComplejo servicioAdministradorComplejo;

    public ControladorComplejoDeportivo(ServicioComplejoDeportivo servicioComplejo,
                                       ServicioAdministradorComplejo servicioAdministradorComplejo) {
        this.servicioComplejo = servicioComplejo;
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
    }

    /**
     * Redirigir de /super-admin/complejos a /super-admin/complejos/listar
     */
    @GetMapping("")
    public String redirectToList() {
        return "redirect:/super-admin/complejos/listar";
    }

    /**
     * Listar todos los complejos
     */
    @GetMapping("/listar")
    public String listarComplejos(Model model) {
        try {
            List<ComplejoDeportivo> complejos = servicioComplejo.obtenerComplejosActivos();
            System.out.println("DEBUG: Número de complejos encontrados: " + complejos.size());
            for (ComplejoDeportivo complejo : complejos) {
                System.out.println("DEBUG: Complejo ID: " + complejo.getId_complejo() + 
                                 ", Nombre: " + complejo.getNombre_complejo() + 
                                 ", Activo: " + complejo.isActivo());
            }
            model.addAttribute("complejos", complejos);
            return "super-admin/complejos/listar";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("mensajeError", "Error al cargar los complejos: " + e.getMessage());
            return "super-admin/complejos/listar";
        }
    }

    /**
     * Mostrar formulario para crear nuevo complejo
     */
    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        try {
            // Agregar un complejo vacío
            model.addAttribute("complejo", new ComplejoDeportivo());
            
            // Debug: Verificar si el servicio de administrador existe
            try {
                model.addAttribute("administradores", servicioAdministradorComplejo.obtenerAdministradoresActivos());
            } catch (Exception e) {
                System.out.println("Error obteniendo administradores: " + e.getMessage());
                model.addAttribute("administradores", java.util.Arrays.asList());
            }
            
            // Debug: Verificar si el servicio de localidades funciona
            try {
                model.addAttribute("localidades", servicioComplejo.obtenerTodasLasLocalidades());
            } catch (Exception e) {
                System.out.println("Error obteniendo localidades: " + e.getMessage());
                model.addAttribute("localidades", java.util.Arrays.asList());
            }
            
            return "super-admin/complejos/formulario";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("mensajeError", "Error al cargar el formulario: " + e.getMessage());
            return "redirect:/super-admin/complejos/listar";
        }
    }

    /**
     * Crear nuevo complejo
     */
    @PostMapping("/crear")
    public String crearComplejo(@RequestParam String nombreComplejo,
                              @RequestParam String direccionComplejo,
                              @RequestParam Long localidadId,
                              @RequestParam Long administradorId,
                              RedirectAttributes redirectAttributes) {
        try {
            servicioComplejo.crearComplejoSimple(nombreComplejo, direccionComplejo, localidadId, administradorId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Complejo deportivo creado exitosamente");
            return "redirect:/super-admin/complejos/listar";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al crear complejo: " + e.getMessage());
            return "redirect:/super-admin/complejos/nuevo";
        }
    }

    /**
     * Ver detalles de un complejo
     */
    @GetMapping("/ver/{id}")
    public String verComplejo(@PathVariable Long id, Model model) {
        try {
            ComplejoDeportivo complejo = servicioComplejo.obtenerPorId(id)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));
            
            model.addAttribute("complejo", complejo);
            return "super-admin/complejos/ver";
        } catch (Exception e) {
            model.addAttribute("mensajeError", "Error al cargar el complejo: " + e.getMessage());
            return "redirect:/super-admin/complejos/listar";
        }
    }

    /**
     * Dar de baja complejo
     */
    @PostMapping("/dar-baja/{id}")
    public String darDeBajaComplejo(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            servicioComplejo.darDeBaja(id);
            redirectAttributes.addFlashAttribute("mensajeExito", "Complejo dado de baja exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al dar de baja complejo: " + e.getMessage());
        }
        return "redirect:/super-admin/complejos/listar";
    }

}