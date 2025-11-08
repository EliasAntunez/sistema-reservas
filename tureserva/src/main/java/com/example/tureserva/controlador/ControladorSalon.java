package com.example.tureserva.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.example.tureserva.servicio.ServicioSalon;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.modelo.Salon;
import com.example.tureserva.modelo.ComplejoDeportivo;

import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin-complejo/salones")
public class ControladorSalon {

	private final ServicioSalon servicioSalon;
	private final ServicioComplejoDeportivo servicioComplejoDeportivo;

	public ControladorSalon(ServicioSalon servicioSalon, ServicioComplejoDeportivo servicioComplejoDeportivo) {
		this.servicioSalon = servicioSalon;
		this.servicioComplejoDeportivo = servicioComplejoDeportivo;
	}

	@GetMapping("/listar/{idComplejo}")
	public String listarSalones(@PathVariable("idComplejo") Long idComplejo,
								@RequestParam(value = "page", defaultValue = "1") int page,
								@RequestParam(value = "size", defaultValue = "10") int size,
								Model model,
								HttpServletRequest request) {

		org.springframework.data.domain.Page<Salon> salonesPage = servicioSalon.listarSalonesPorComplejoPaginado(idComplejo, page, size);
		
		// Generar mapa de estados de configuración para cada salón
		Map<Long, String> estadosConfiguracion = new HashMap<>();
		for (Salon salon : salonesPage.getContent()) {
			boolean tienePoliticaSenia = salon.getPoliticaSenia() != null;
			boolean tienePoliticaCancelacion = salon.getPoliticaCancelacion() != null;
			
			if (tienePoliticaSenia && tienePoliticaCancelacion) {
				estadosConfiguracion.put(salon.getId(), "completo");
			} else {
				estadosConfiguracion.put(salon.getId(), "incompleto");
			}
		}
		
		model.addAttribute("salonesPage", salonesPage);
		model.addAttribute("estadosConfiguracion", estadosConfiguracion);
		model.addAttribute("idComplejo", idComplejo);
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", salonesPage.getTotalPages());

		String requestedWithHeader = request.getHeader("X-Requested-With");
		if ("XMLHttpRequest".equals(requestedWithHeader)) {
			return "admin-complejo/fragments/salones-fragment :: contenido-actualizable";
		}

		// Para navegación directa (no-AJAX), redirigimos a la vista integral de espacios
		return "redirect:/admin-complejo/espacios/listar/" + idComplejo;
	}

	@DeleteMapping("/eliminar/{id}")
	@ResponseBody
	public ResponseEntity<Map<String, String>> eliminarSalonDelete(@PathVariable("id") Long id) {
		try {
			servicioSalon.eliminarSalon(id);
			Map<String, String> response = new HashMap<>();
			response.put("status", "success");
			response.put("message", "Salón eliminado correctamente.");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, String> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", "Error al eliminar el salón: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@GetMapping("/crear/{idComplejo}")
	public String mostrarFormularioCrear(@PathVariable("idComplejo") Long idComplejo, Model model) {
		Salon salon = new Salon();
		model.addAttribute("salon", salon);
		model.addAttribute("idComplejo", idComplejo);
		return "admin-complejo/salones/crear";
	}

	@PostMapping("/crear")
	public String procesarFormularioCrear(@Valid @ModelAttribute("salon") Salon salon,
										  BindingResult bindingResult,
										  @RequestParam("idComplejo") Long idComplejo,
										  Model model,
										  RedirectAttributes redirectAttributes) {

		if (bindingResult.hasErrors()) {
			model.addAttribute("idComplejo", idComplejo);
			return "admin-complejo/salones/crear";
		}

		// Validación de nombre duplicado ANTES de intentar guardar
		try {
			Optional<ComplejoDeportivo> complejoDeportivoOpt = servicioComplejoDeportivo.obtenerPorId(idComplejo);
			
			if (!complejoDeportivoOpt.isPresent()) {
				redirectAttributes.addFlashAttribute("error", "Error: Complejo deportivo no encontrado (ID: " + idComplejo + ").");
				return "redirect:/admin-complejo/salones/crear/" + idComplejo;
			}

			ComplejoDeportivo complejoDeportivo = complejoDeportivoOpt.get();

			// Verificar si ya existe un salón con ese nombre en el complejo
			if (servicioSalon.existeNombreDuplicado(salon.getNombre(), complejoDeportivo)) {
				model.addAttribute("error", "Ya existe un salón con ese nombre en este complejo deportivo");
				model.addAttribute("idComplejo", idComplejo);
				return "admin-complejo/salones/crear";
			}

			// Si todo está OK, guardamos
			salon.setComplejoDeportivo(complejoDeportivo);
			servicioSalon.guardarSalon(salon);
			redirectAttributes.addFlashAttribute("exito", "Salón creado exitosamente.");
			return "redirect:/admin-complejo/espacios/listar/" + idComplejo + "?tab=salones";
		} catch (DataIntegrityViolationException e) {
			// Manejar error de nombre duplicado (constraint de unicidad)
			String mensajeError = "Ya existe un salón con ese nombre en este complejo deportivo";
			
			// Verificar si es el constraint de nombre único
			if (e.getMessage() != null && e.getMessage().toLowerCase().contains("uk_espacio_nombre_complejo")) {
				mensajeError = "Ya existe un salón con ese nombre en este complejo deportivo";
			} else {
				mensajeError = "Error al guardar el salón. Verifique los datos ingresados";
			}
			
			model.addAttribute("error", mensajeError);
			model.addAttribute("idComplejo", idComplejo);
			return "admin-complejo/salones/crear";
		}
	}

	@GetMapping("/modificar/{id}")
	public String mostrarFormularioModificar(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
		Optional<Salon> salonOpt = servicioSalon.obtenerPorId(id);
		if (salonOpt.isPresent()) {
			model.addAttribute("salon", salonOpt.get());
			model.addAttribute("idComplejo", salonOpt.get().getComplejoDeportivo().getId_complejo());
			return "admin-complejo/salones/modificar";
		} else {
			redirectAttributes.addFlashAttribute("error", "Salón no encontrado.");
			return "redirect:/admin-complejo/espacios/listar";
		}
	}

	@PostMapping("/modificar")
	public String procesarFormularioModificar(@Valid @ModelAttribute("salon") Salon salon,
											  @RequestParam("idComplejo") Long idComplejo,
											  BindingResult bindingResult,
											  Model model,
											  RedirectAttributes redirectAttributes) {

		if (bindingResult.hasErrors()) {
			model.addAttribute("idComplejo", idComplejo);
			return "admin-complejo/salones/modificar";
		}

		// Validación de nombre duplicado ANTES de intentar actualizar
		try {
			Optional<ComplejoDeportivo> complejoDeportivoOpt = servicioComplejoDeportivo.obtenerPorId(idComplejo);
			
			if (!complejoDeportivoOpt.isPresent()) {
				redirectAttributes.addFlashAttribute("error", "Error: Complejo deportivo no encontrado (ID: " + idComplejo + ").");
				return "redirect:/admin-complejo/salones/modificar/" + salon.getId();
			}

			ComplejoDeportivo complejoDeportivo = complejoDeportivoOpt.get();

			// Verificar si existe otro salón con ese nombre (excluyendo el actual)
			if (servicioSalon.existeNombreDuplicadoExcluyendoId(salon.getNombre(), complejoDeportivo, salon.getId())) {
				model.addAttribute("error", "Ya existe un salón con ese nombre en este complejo deportivo");
				model.addAttribute("idComplejo", idComplejo);
				return "admin-complejo/salones/modificar";
			}

			// Si todo está OK, actualizamos
			salon.setComplejoDeportivo(complejoDeportivo);
			servicioSalon.actualizarSalon(salon);
			redirectAttributes.addFlashAttribute("exito", "Salón actualizado exitosamente.");
			return "redirect:/admin-complejo/espacios/listar/" + idComplejo + "?tab=salones";
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", "Error al actualizar el salón: " + e.getMessage());
			return "redirect:/admin-complejo/salones/modificar/" + salon.getId();
		}
	}

}
