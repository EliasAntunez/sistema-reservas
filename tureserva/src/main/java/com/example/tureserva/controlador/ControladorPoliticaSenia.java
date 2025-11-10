package com.example.tureserva.controlador;

import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioPoliticaSenia;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.tureserva.modelo.PoliticaSenia;
import org.springframework.ui.Model;
import com.example.tureserva.modelo.ComplejoDeportivo;
import java.util.Optional;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequestMapping("/admin-complejo/politicas-senia")
public class ControladorPoliticaSenia {

    @Autowired
    private ServicioPoliticaSenia servicioPoliticaSenia;
    @Autowired
    private ServicioComplejoDeportivo servicioComplejoDeportivo;

    ControladorPoliticaSenia(ServicioComplejoDeportivo servicioComplejoDeportivo) {
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
    }

    // redirigir a listar politicas de seña
    @GetMapping("/")
    public String redirigirListarPoliticaSenia() {
        return "redirect:/admin-complejo/politicas-senia/listar";
    }

    // listar politicas de seña
    @GetMapping("/listar/{complejoId}")
    public String listarPoliticasSenia(@PathVariable Long complejoId, Model model) {
        model.addAttribute("politicasSenia", servicioPoliticaSenia.listarPoliticasSeniaPorComplejoId(complejoId));
        return "admin-complejo/politica-senia/listar";
    }

    //crear nueva politica de seña
    @GetMapping("/crear/{complejoId}")
    public String crearPoliticaSenia(@PathVariable Long complejoId, Model model) {
        PoliticaSenia politicaSenia = new PoliticaSenia();
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            politicaSenia.setComplejoDeportivo(complejoDeportivo.get());
        }
        model.addAttribute("nombreComplejo", complejoDeportivo.get().getNombre_complejo());
        model.addAttribute("politicaSenia", politicaSenia);
        return "admin-complejo/politica-senia/crear";
    }

    @PostMapping("/crear/{complejoId}")
    public String crearPoliticaSenia(
        @PathVariable Long complejoId,
        @ModelAttribute PoliticaSenia politicaSenia,
        BindingResult result,
        RedirectAttributes redirectAttributes
    ) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Datos inválidos. Verifique los campos e intente nuevamente.");
            return "redirect:/admin-complejo/politicas-senia/crear/" + complejoId;
        }
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            politicaSenia.setComplejoDeportivo(complejoDeportivo.get());
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró el complejo deportivo.");
            return "redirect:/admin-complejo/politicas-senia/listar";
        }
        // Validaciones de negocio
        if ((politicaSenia.getPorcentajeSenia() == null || politicaSenia.getPorcentajeSenia() <= 0) && (politicaSenia.getMontoFijo() == null || politicaSenia.getMontoFijo() <= 0)) {
            redirectAttributes.addFlashAttribute("error", "Debe ingresar un porcentaje de seña o un monto fijo.");
            return "redirect:/admin-complejo/politicas-senia/crear/" + complejoId;
        }
        if (politicaSenia.getFechaFinVigencia().isBefore(politicaSenia.getFechaInicioVigencia())) {
            redirectAttributes.addFlashAttribute("error", "La fecha de fin de vigencia no puede ser anterior a la fecha de inicio.");
            return "redirect:/admin-complejo/politicas-senia/crear/" + complejoId;
        }
        servicioPoliticaSenia.guardarPoliticaSenia(politicaSenia);
        redirectAttributes.addFlashAttribute("exito", "Política de seña creada correctamente.");
        return "redirect:/admin-complejo/politicas-senia/listar/" + complejoId;
    }

    //eliminar politica de seña
    @PostMapping("/eliminar/{politicaId}")
    public String darDeBajaPoliticaSenia(@PathVariable Long politicaId) {
        Long complejoId = servicioPoliticaSenia.obtenerComplejoIdPorPoliticaId(politicaId);
        servicioPoliticaSenia.darDeBajaPoliticaSenia(politicaId);
        return "redirect:/admin-complejo/politicas-senia/listar/" + complejoId;
    }

    //modificar politica de seña
    @GetMapping("/modificar/{politicaId}")
    public String modificarPoliticaSenia(@PathVariable Long politicaId, Model model) {
        PoliticaSenia politicaSenia = servicioPoliticaSenia.obtenerPoliticaSeniaPorId(politicaId);
        if (politicaSenia != null) {
            model.addAttribute("politicaSenia", politicaSenia);
            model.addAttribute("complejoId", politicaSenia.getComplejoDeportivo().getId_complejo()); // <-- AGREGAR ESTO
            return "admin-complejo/politica-senia/modificar";
        }
        return "redirect:/admin-complejo/politicas-senia/listar";   
    }

    @PostMapping("/modificar/{complejoId}/{politicaId}")
    public String modificarPoliticaSenia(
        @PathVariable Long complejoId,
        @PathVariable Long politicaId,
        @ModelAttribute PoliticaSenia politicaSenia,
        BindingResult result,
        RedirectAttributes redirectAttributes
    ) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Datos inválidos. Verifique los campos e intente nuevamente.");
            return "redirect:/admin-complejo/politicas-senia/modificar/" + politicaId;
        }
        politicaSenia.setId(politicaId);
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            politicaSenia.setComplejoDeportivo(complejoDeportivo.get());
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró el complejo deportivo.");
            return "redirect:/admin-complejo/politicas-senia/listar";
        }
        // Validaciones de negocio
        if ((politicaSenia.getPorcentajeSenia() == null || politicaSenia.getPorcentajeSenia() <= 0) && (politicaSenia.getMontoFijo() == null || politicaSenia.getMontoFijo() <= 0)) {
            redirectAttributes.addFlashAttribute("error", "Debe ingresar un porcentaje de seña o un monto fijo.");
            return "redirect:/admin-complejo/politicas-senia/modificar/" + politicaId;
        }
        if (politicaSenia.getFechaFinVigencia().isBefore(politicaSenia.getFechaInicioVigencia())) {
            redirectAttributes.addFlashAttribute("error", "La fecha de fin de vigencia no puede ser anterior a la fecha de inicio.");
            return "redirect:/admin-complejo/politicas-senia/modificar/" + politicaId;
        }
        servicioPoliticaSenia.actualizarPoliticaSenia(politicaSenia);
        redirectAttributes.addFlashAttribute("exito", "Política de seña modificada correctamente.");
        return "redirect:/admin-complejo/politicas-senia/listar/" + complejoId;
    }
}