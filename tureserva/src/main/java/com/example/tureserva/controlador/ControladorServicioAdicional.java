package com.example.tureserva.controlador;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.ServicioAdicional;
import com.example.tureserva.servicio.ServicioServicioAdicional;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;

@Controller
@RequestMapping("admin-complejo/servicios-adicionales")
public class ControladorServicioAdicional {
    @Autowired
    private ServicioServicioAdicional servicioServicioAdicional;
    @Autowired
    private ServicioComplejoDeportivo servicioComplejoDeportivo;

    @GetMapping("/")
    public String redirigirListarServiciosAdicionales() {
        return "redirect:/servicios-adicionales/listar";
    }

    @GetMapping("/listar/{idComplejo}")
    public String listarServiciosAdicionales(@PathVariable Long idComplejo, Model model) {
        model.addAttribute("idComplejo", idComplejo);
        model.addAttribute("serviciosAdicionales", servicioServicioAdicional.obtenerServiciosAdicionalesPorComplejoYActivoTrue(idComplejo));
        return "admin-complejo/servicios-adicionales/listar";
    }

    @GetMapping("/crear/{idComplejo}")
    public String crearServicioAdicional(@PathVariable Long idComplejo, Model model) {
        model.addAttribute("idComplejo", idComplejo);
        model.addAttribute("servicioAdicional", new ServicioAdicional());
        return "admin-complejo/servicios-adicionales/crear";
    }

    @PostMapping("/crear/{idComplejo}")
    public String crearServicioAdicional(@PathVariable Long idComplejo,
                                         @Valid @ModelAttribute("servicioAdicional") ServicioAdicional servicioAdicional,
                                         BindingResult bindingResult,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("idComplejo", idComplejo);
            return "admin-complejo/servicios-adicionales/crear";
        }

        // Verificar existencia del complejo
        java.util.Optional<ComplejoDeportivo> complejoOpt = servicioComplejoDeportivo.obtenerPorId(idComplejo);
        if (!complejoOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Error: Complejo deportivo no encontrado (ID: " + idComplejo + ").");
            return "redirect:/admin-complejo/servicios-adicionales/crear/" + idComplejo;
        }

        servicioAdicional.setComplejoDeportivo(complejoOpt.get());

        // Pre-check duplicado por nombre dentro del mismo complejo
        if (servicioServicioAdicional.existeNombreEnComplejo(servicioAdicional.getNombre(), complejoOpt.get())) {
            model.addAttribute("error", "Ya existe un servicio con ese nombre en este complejo deportivo");
            model.addAttribute("idComplejo", idComplejo);
            return "admin-complejo/servicios-adicionales/crear";
        }

        try {
            servicioServicioAdicional.guardarServicioAdicional(servicioAdicional);
        } catch (ConstraintViolationException e) {
            // Mapear errores de ConstraintViolation a BindingResult para mostrar en la vista
            for (jakarta.validation.ConstraintViolation<?> v : e.getConstraintViolations()) {
                String prop = v.getPropertyPath().toString();
                bindingResult.rejectValue(prop, "error.servicioAdicional", v.getMessage());
            }
            model.addAttribute("idComplejo", idComplejo);
            return "admin-complejo/servicios-adicionales/crear";
        } catch (DataIntegrityViolationException e) {
            // Manejar constraint de unicidad u otros errores de BD
            model.addAttribute("error", "Ya existe un servicio con ese nombre en este complejo deportivo o los datos son inválidos.");
            model.addAttribute("idComplejo", idComplejo);
            return "admin-complejo/servicios-adicionales/crear";
        }

        redirectAttributes.addFlashAttribute("exito", "Servicio adicional creado correctamente.");
        return "redirect:/admin-complejo/servicios-adicionales/listar/" + idComplejo;
    }

    @PostMapping("/eliminar/{idServicio}")
    public String eliminarServicioAdicional(@PathVariable Long idServicio, RedirectAttributes redirectAttributes) {
        java.util.Optional<ServicioAdicional> servicioOpt = servicioServicioAdicional.obtenerPorId(idServicio);
        if (!servicioOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Servicio adicional no encontrado.");
            return "redirect:/admin-complejo/servicios-adicionales/listar/"; // fallback
        }
        Long idComplejo = servicioOpt.get().getComplejoDeportivo().getId_complejo();
        servicioServicioAdicional.darDeBajaServicioAdicional(idServicio);
        redirectAttributes.addFlashAttribute("exito", "Servicio dado de baja correctamente.");
        return "redirect:/admin-complejo/servicios-adicionales/listar/" + idComplejo;
    }

    @GetMapping("/modificar/{idServicio}")
    public String modificarServicioAdicional(@PathVariable Long idServicio, Model model) {
        ServicioAdicional servicioAdicional = servicioServicioAdicional.findById(idServicio);
        model.addAttribute("servicioAdicional", servicioAdicional);
        // Agregar ambos nombres por compatibilidad con plantillas
        model.addAttribute("idComplejo", servicioAdicional.getComplejoDeportivo().getId_complejo());
        model.addAttribute("complejoId", servicioAdicional.getComplejoDeportivo().getId_complejo());
        return "admin-complejo/servicios-adicionales/modificar";
    }
    @PostMapping("/modificar/{idServicio}")
    public String modificarServicioAdicional(@PathVariable Long idServicio,
                                             @Valid @ModelAttribute("servicioAdicional") ServicioAdicional servicioAdicional,
                                             BindingResult bindingResult,
                                             Model model,
                                             RedirectAttributes redirectAttributes) {

        java.util.Optional<ServicioAdicional> servicioExistenteOpt = servicioServicioAdicional.obtenerPorId(idServicio);
        if (!servicioExistenteOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Servicio adicional no encontrado.");
            return "redirect:/admin-complejo/servicios-adicionales/listar/"; // fallback
        }

        ServicioAdicional servicioExistente = servicioExistenteOpt.get();
        Long idComplejo = servicioExistente.getComplejoDeportivo().getId_complejo();

        if (bindingResult.hasErrors()) {
            model.addAttribute("servicioAdicional", servicioAdicional);
            model.addAttribute("idComplejo", idComplejo);
            model.addAttribute("complejoId", idComplejo);
            return "admin-complejo/servicios-adicionales/modificar";
        }
        // Pre-check duplicado (excluyendo el propio id)
        if (servicioServicioAdicional.existeNombreEnComplejoExcluyendoId(servicioAdicional.getNombre(), servicioExistente.getComplejoDeportivo(), idServicio)) {
            model.addAttribute("error", "Ya existe otro servicio con ese nombre en este complejo deportivo.");
            model.addAttribute("idComplejo", idComplejo);
            model.addAttribute("complejoId", idComplejo);
            return "admin-complejo/servicios-adicionales/modificar";
        }
        servicioExistente.setNombre(servicioAdicional.getNombre());
        servicioExistente.setPrecio(servicioAdicional.getPrecio());
        servicioExistente.setTipoDeCobro(servicioAdicional.getTipoDeCobro());
        servicioExistente.setAplicableA(servicioAdicional.getAplicableA());
        // Nuevo: persistir la cantidad máxima y el flag activo si se editan
        servicioExistente.setMaximoCantidad(servicioAdicional.getMaximoCantidad());
        servicioExistente.setActivo(servicioAdicional.getActivo());

        try {
            servicioServicioAdicional.guardarServicioAdicional(servicioExistente);
        } catch (ConstraintViolationException e) {
            for (jakarta.validation.ConstraintViolation<?> v : e.getConstraintViolations()) {
                String prop = v.getPropertyPath().toString();
                bindingResult.rejectValue(prop, "error.servicioAdicional", v.getMessage());
            }
            model.addAttribute("idComplejo", idComplejo);
            model.addAttribute("complejoId", idComplejo);
            return "admin-complejo/servicios-adicionales/modificar";
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error", "Ya existe un servicio con ese nombre en este complejo deportivo o los datos son inválidos.");
            model.addAttribute("idComplejo", idComplejo);
            model.addAttribute("complejoId", idComplejo);
            return "admin-complejo/servicios-adicionales/modificar";
        }

        redirectAttributes.addFlashAttribute("exito", "Servicio actualizado correctamente.");
        return "redirect:/admin-complejo/servicios-adicionales/listar/" + idComplejo;
    }

}