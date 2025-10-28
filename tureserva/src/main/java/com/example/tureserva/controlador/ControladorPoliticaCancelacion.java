package com.example.tureserva.controlador;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioPoliticaCancelacion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.example.tureserva.modelo.PoliticaCancelacion;
import org.springframework.ui.Model;
import com.example.tureserva.modelo.ComplejoDeportivo;
import java.util.Optional;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin-complejo/politicas-cancelacion")
public class ControladorPoliticaCancelacion {

    @Autowired
    private ServicioPoliticaCancelacion servicioPoliticaCancelacion;
    @Autowired
    private ServicioComplejoDeportivo servicioComplejoDeportivo;

    // Redirigir a listar
    @GetMapping("/")
    public String redirigirListarPoliticaCancelacion() {
        return "redirect:/admin-complejo/politicas-cancelacion/listar";
    }

    // Listar políticas de cancelación
    @GetMapping("/listar/{complejoId}")
    public String listarPoliticasCancelacion(@PathVariable Long complejoId, Model model) {
        model.addAttribute("politicasCancelacion", servicioPoliticaCancelacion.listarPoliticasCancelacionPorComplejoId(complejoId));
        return "admin-complejo/politica-cancelacion/listar";
    }

    // Crear nueva política
    @GetMapping("/crear/{complejoId}")
    public String crearPoliticaCancelacion(@PathVariable Long complejoId, Model model, RedirectAttributes redirectAttributes) {
        PoliticaCancelacion politicaCancelacion = new PoliticaCancelacion();
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            politicaCancelacion.setComplejoDeportivo(complejoDeportivo.get());
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró el complejo deportivo.");
            return "redirect:/admin-complejo/politicas-cancelacion/listar";
        }
        model.addAttribute("politicaCancelacion", politicaCancelacion);
        return "admin-complejo/politica-cancelacion/crear";
    }

    @PostMapping("/crear/{complejoId}")
    public String crearPoliticaCancelacion(@PathVariable Long complejoId, @ModelAttribute PoliticaCancelacion politicaCancelacion, RedirectAttributes redirectAttributes) {
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            politicaCancelacion.setComplejoDeportivo(complejoDeportivo.get());
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró el complejo deportivo.");
            return "redirect:/admin-complejo/politicas-cancelacion/listar";
        }
        // Validación de negocio: porcentaje > 0
        if (politicaCancelacion.getPorcentajeDevolucion() == null || politicaCancelacion.getPorcentajeDevolucion() <= 0) {
            redirectAttributes.addFlashAttribute("error", "El porcentaje de devolución debe ser mayor a 0.");
            return "redirect:/admin-complejo/politicas-cancelacion/crear/" + complejoId;
        }
        // Validación de negocio: fecha fin >= fecha inicio
        if (politicaCancelacion.getFechaInicioVigencia() != null && politicaCancelacion.getFechaFinVigencia() != null && politicaCancelacion.getFechaFinVigencia().isBefore(politicaCancelacion.getFechaInicioVigencia())) {
            redirectAttributes.addFlashAttribute("error", "La fecha de fin de vigencia no puede ser anterior a la fecha de inicio.");
            return "redirect:/admin-complejo/politicas-cancelacion/crear/" + complejoId;
        }
        servicioPoliticaCancelacion.guardarPoliticaCancelacion(politicaCancelacion);
        redirectAttributes.addFlashAttribute("exito", "Política de cancelación creada correctamente.");
        return "redirect:/admin-complejo/politicas-cancelacion/listar/" + complejoId;
    }

    // Eliminar política
    @PostMapping("/eliminar/{politicaId}")
    public String darDeBajaPoliticaCancelacion(@PathVariable Long politicaId) {
        Long complejoId = servicioPoliticaCancelacion.obtenerComplejoIdPorPoliticaId(politicaId);
        servicioPoliticaCancelacion.darDeBajaPoliticaCancelacion(politicaId);
        return "redirect:/admin-complejo/politicas-cancelacion/listar/" + complejoId;
    }

    // Modificar política
    @GetMapping("/modificar/{politicaId}")
    public String modificarPoliticaCancelacion(@PathVariable Long politicaId, Model model, RedirectAttributes redirectAttributes) {
        PoliticaCancelacion politicaCancelacion = servicioPoliticaCancelacion.obtenerPoliticaCancelacionPorId(politicaId);
        if (politicaCancelacion != null) {
            model.addAttribute("politicaCancelacion", politicaCancelacion);
            model.addAttribute("complejoId", politicaCancelacion.getComplejoDeportivo().getId_complejo());
            return "admin-complejo/politica-cancelacion/modificar";
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró la política de cancelación.");
            return "redirect:/admin-complejo/politicas-cancelacion/listar";
        }
    }

    @PostMapping("/modificar/{complejoId}/{politicaId}")
    public String modificarPoliticaCancelacion(
        @PathVariable Long complejoId,
        @PathVariable Long politicaId,
        @Valid @ModelAttribute PoliticaCancelacion politicaCancelacion,
        BindingResult result,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        politicaCancelacion.setId(politicaId);
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            politicaCancelacion.setComplejoDeportivo(complejoDeportivo.get());
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró el complejo deportivo.");
            return "redirect:/admin-complejo/politicas-cancelacion/listar";
        }
        if (result.hasErrors()) {
            model.addAttribute("politicaCancelacion", politicaCancelacion);
            model.addAttribute("complejoId", complejoId);
            return "admin-complejo/politica-cancelacion/modificar";
        }
        servicioPoliticaCancelacion.actualizarPoliticaCancelacion(politicaCancelacion);
        redirectAttributes.addFlashAttribute("exito", "Política de cancelación modificada correctamente.");
        return "redirect:/admin-complejo/politicas-cancelacion/listar/" + complejoId;
    }
}
