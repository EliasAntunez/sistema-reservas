package com.example.tureserva.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.HashMap;

import com.example.tureserva.modelo.Cancha;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.enums.TipoPiso;
import com.example.tureserva.servicio.ServicioCancha;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioCanchaDeporte;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;


@Controller
@RequestMapping("/admin-complejo/canchas")
public class ControladorCancha {
    
    private final ServicioCancha servicioCancha;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;
    private final ServicioCanchaDeporte servicioCanchaDeporte;
    
    public ControladorCancha(ServicioCancha servicioCancha, 
                            ServicioComplejoDeportivo servicioComplejoDeportivo,
                            ServicioCanchaDeporte servicioCanchaDeporte) {
        this.servicioCancha = servicioCancha;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
        this.servicioCanchaDeporte = servicioCanchaDeporte;
    }

    
    @GetMapping("")
    public String redirigirAListar() {
        return "redirect:/admin-complejo/canchas/listar";
    }

    
    @GetMapping("/listar/{idComplejo}")
    public String listarCanchas(@PathVariable("idComplejo") Long idComplejo,
                                @RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "size", defaultValue = "10") int size,
                                Model model,
                                HttpServletRequest request) {
        org.springframework.data.domain.Page<com.example.tureserva.modelo.Cancha> canchasPage = servicioCancha.listarCanchasPorComplejoPaginado(idComplejo, page, size);
        
        // Generar mapa de estados de configuración para cada cancha
        Map<Long, String> estadosConfiguracion = new HashMap<>();
        for (Cancha cancha : canchasPage.getContent()) {
            boolean tieneDeportes = servicioCanchaDeporte.canchaTieneDeportes(cancha.getId());
            boolean tienePoliticaSenia = cancha.getPoliticaSenia() != null;
            boolean tienePoliticaCancelacion = cancha.getPoliticaCancelacion() != null;
            
            if (tieneDeportes && tienePoliticaSenia && tienePoliticaCancelacion) {
                estadosConfiguracion.put(cancha.getId(), "completo");
            } else {
                estadosConfiguracion.put(cancha.getId(), "incompleto");
            }
        }
        
        model.addAttribute("canchasPage", canchasPage);
        model.addAttribute("estadosConfiguracion", estadosConfiguracion);
        model.addAttribute("idComplejo", idComplejo);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", canchasPage.getTotalPages());
        // AJAX fragment
        String requestedWithHeader = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWithHeader)) {
            return "admin-complejo/fragments/canchas-fragment :: contenido-actualizable";
        }
        // Para peticiones normales, redirigimos a la vista integrada de espacios
        return "redirect:/admin-complejo/espacios/listar/" + idComplejo;
    }

    @DeleteMapping("/eliminar/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> eliminarCanchaDelete(@PathVariable("id") Long id) {
        try {
            servicioCancha.eliminarCancha(id);
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Cancha eliminada correctamente.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error al eliminar la cancha: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/crear/{idComplejo}")
    public String mostrarFormularioCrear(@PathVariable("idComplejo") Long idComplejo, Model model) {
        Cancha cancha = new Cancha();
        model.addAttribute("cancha", cancha);
        model.addAttribute("idComplejo", idComplejo);
        model.addAttribute("tipoPiso", TipoPiso.values());
        return "admin-complejo/canchas/crear";
    }

    @PostMapping("/crear")
    public String procesarFormularioCrear(
                @Valid @ModelAttribute("cancha") Cancha cancha, // 1. Recibe el objeto y lo valida
                BindingResult bindingResult,                     // 2. Aquí se guardan los errores de validación
                @RequestParam("idComplejo") Long idComplejo,     // 3. Recibe el ID del <input hidden>
                Model model,                                     // 4. Para devolver datos al form SI HAY ERROR
                RedirectAttributes redirectAttributes) {         // 5. Para enviar mensajes de éxito/error DESPUÉS de redirigir

        // --- A. Si hay errores de validación ---
        if (bindingResult.hasErrors()) {

            model.addAttribute("idComplejo", idComplejo); 
            model.addAttribute("tipoPiso", TipoPiso.values());

            // Devolvemos la vista del formulario (NO redirigimos)
            return "admin-complejo/canchas/crear"; 
        }

        // --- B. Validación de nombre duplicado ANTES de intentar guardar ---
        try {
            Optional<ComplejoDeportivo> complejoDeportivoOpt = servicioComplejoDeportivo.obtenerPorId(idComplejo);

            if (!complejoDeportivoOpt.isPresent()) {
                // Error: El complejo no existe
                redirectAttributes.addFlashAttribute("error", "Error: Complejo deportivo no encontrado (ID: " + idComplejo + ").");
                return "redirect:/admin-complejo/canchas/crear/" + idComplejo;
            }

            ComplejoDeportivo complejoDeportivo = complejoDeportivoOpt.get();

            // Verificar si ya existe una cancha con ese nombre en el complejo
            if (servicioCancha.existeNombreDuplicado(cancha.getNombre(), complejoDeportivo)) {
                model.addAttribute("error", "Ya existe una cancha con ese nombre en este complejo deportivo");
                model.addAttribute("idComplejo", idComplejo);
                model.addAttribute("tipoPiso", TipoPiso.values());
                return "admin-complejo/canchas/crear";
            }

            // Si todo está OK, guardamos
            cancha.setComplejoDeportivo(complejoDeportivo);
            servicioCancha.guardarCancha(cancha);
            redirectAttributes.addFlashAttribute("exito", "Cancha creada exitosamente.");
            
            // Redirigimos directamente al listado de espacios para que el flash
            // attribute llegue a la plantilla `admin-complejo/espacios/listar.html`.
            return "redirect:/admin-complejo/espacios/listar/" + idComplejo;

        } catch (DataIntegrityViolationException e) {
            // Manejar error de nombre duplicado (constraint de unicidad)
            String mensajeError = "Ya existe una cancha con ese nombre en este complejo deportivo";
            
            // Verificar si es el constraint de nombre único
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("uk_espacio_nombre_complejo")) {
                mensajeError = "Ya existe una cancha con ese nombre en este complejo deportivo";
            } else {
                mensajeError = "Error al guardar la cancha. Verifique los datos ingresados";
            }
            
            model.addAttribute("error", mensajeError);
            model.addAttribute("idComplejo", idComplejo);
            model.addAttribute("tipoPiso", TipoPiso.values());
            return "admin-complejo/canchas/crear";
        }
    }

    @GetMapping("/modificar/{id}")
    public String mostrarFormularioModificar(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Cancha> canchaOpt = servicioCancha.obtenerPorId(id);
        Long idComplejo = canchaOpt.get().getComplejoDeportivo().getId_complejo();
        if (canchaOpt.isPresent() && canchaOpt.get().getActivo()) {
            model.addAttribute("cancha", canchaOpt.get());
            model.addAttribute("tipoPiso", TipoPiso.values());
            model.addAttribute("idComplejo", canchaOpt.get().getComplejoDeportivo().getId_complejo());
            return "admin-complejo/canchas/modificar";
        } else {
            redirectAttributes.addFlashAttribute("error", "Cancha no encontrada.");
            return "redirect:/admin-complejo/espacios/listar/" + idComplejo;
        }
    }

    @PostMapping("/modificar")
    public String procesarFormularioModificar(
                @Valid @ModelAttribute("cancha") Cancha cancha,
                @RequestParam("idComplejo") Long idComplejo,
                BindingResult bindingResult,
                Model model,
                RedirectAttributes redirectAttributes) {

        // --- A. Si hay errores de validación ---
        if (bindingResult.hasErrors()) {
            model.addAttribute("tipoPiso", TipoPiso.values());
            model.addAttribute("idComplejo", idComplejo);
            return "admin-complejo/canchas/modificar";
        }

        // --- B. Validación de nombre duplicado ANTES de intentar actualizar ---
        try {
            Optional<ComplejoDeportivo> complejoDeportivoOpt = servicioComplejoDeportivo.obtenerPorId(idComplejo);
            
            if (!complejoDeportivoOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Error: Complejo deportivo no encontrado (ID: " + idComplejo + ").");
                return "redirect:/admin-complejo/canchas/modificar/" + cancha.getId();
            }

            ComplejoDeportivo complejoDeportivo = complejoDeportivoOpt.get();

            // Verificar si existe otra cancha con ese nombre (excluyendo la actual)
            if (servicioCancha.existeNombreDuplicadoExcluyendoId(cancha.getNombre(), complejoDeportivo, cancha.getId())) {
                model.addAttribute("error", "Ya existe una cancha con ese nombre en este complejo deportivo");
                model.addAttribute("tipoPiso", TipoPiso.values());
                model.addAttribute("idComplejo", idComplejo);
                return "admin-complejo/canchas/modificar";
            }

            // Si todo está OK, actualizamos
            cancha.setComplejoDeportivo(complejoDeportivo);
            servicioCancha.actualizarCancha(cancha);
            redirectAttributes.addFlashAttribute("exito", "Cancha actualizada exitosamente.");

            // Redirigir al listado de espacios para que el mensaje se muestre
            return "redirect:/admin-complejo/espacios/listar/" + idComplejo;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar la cancha: " + e.getMessage());
            return "redirect:/admin-complejo/canchas/modificar/" + cancha.getId();
        }
    }
}
