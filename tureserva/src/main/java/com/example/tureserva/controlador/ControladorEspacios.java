package com.example.tureserva.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

import com.example.tureserva.servicio.ServicioCancha;
import com.example.tureserva.servicio.ServicioSalon;
import com.example.tureserva.servicio.ServicioDeporte;
import com.example.tureserva.servicio.ServicioCanchaDeporte;
import com.example.tureserva.servicio.ServicioEspacioReservable;
import com.example.tureserva.servicio.ServicioPoliticaSenia;
import com.example.tureserva.servicio.ServicioPoliticaCancelacion;
import com.example.tureserva.modelo.Cancha;
import com.example.tureserva.modelo.Salon;
import com.example.tureserva.modelo.Deporte;
import com.example.tureserva.modelo.CanchaDeporte;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.modelo.PoliticaSenia;
import com.example.tureserva.modelo.PoliticaCancelacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin-complejo/espacios")
public class ControladorEspacios {

    private final ServicioCancha servicioCancha;
    private final ServicioSalon servicioSalon;
    private final ServicioDeporte servicioDeporte;
    private final ServicioCanchaDeporte servicioCanchaDeporte;
    private final ServicioEspacioReservable servicioEspacioReservable;
    private final ServicioPoliticaSenia servicioPoliticaSenia;
    private final ServicioPoliticaCancelacion servicioPoliticaCancelacion;

    public ControladorEspacios(ServicioCancha servicioCancha, 
                              ServicioSalon servicioSalon,
                              ServicioDeporte servicioDeporte,
                              ServicioCanchaDeporte servicioCanchaDeporte,
                              ServicioEspacioReservable servicioEspacioReservable,
                              ServicioPoliticaSenia servicioPoliticaSenia,
                              ServicioPoliticaCancelacion servicioPoliticaCancelacion) {
        this.servicioCancha = servicioCancha;
        this.servicioSalon = servicioSalon;
        this.servicioDeporte = servicioDeporte;
        this.servicioCanchaDeporte = servicioCanchaDeporte;
        this.servicioEspacioReservable = servicioEspacioReservable;
        this.servicioPoliticaSenia = servicioPoliticaSenia;
        this.servicioPoliticaCancelacion = servicioPoliticaCancelacion;
    }

    @GetMapping("/listar/{idComplejo}")
    public String listar(@PathVariable("idComplejo") Long idComplejo, Model model) {
        model.addAttribute("idComplejo", idComplejo);
        return "admin-complejo/espacios/listar";
    }

    /**
     * Vista principal de gestión de un espacio con tabs
     */
    @GetMapping("/gestionar/{id}")
    public String mostrarGestion(@PathVariable("id") Long id, 
                                 @RequestParam(value = "tab", defaultValue = "deportes") String tab,
                                 Model model, 
                                 RedirectAttributes redirectAttributes) {
        
        // Intentar cargar primero como Cancha
        Optional<Cancha> canchaOpt = servicioCancha.obtenerPorId(id);
        if (canchaOpt.isPresent()) {
            model.addAttribute("espacio", canchaOpt.get());
            model.addAttribute("tipoEspacio", "Cancha");
            model.addAttribute("esCancha", true);
            model.addAttribute("idComplejo", canchaOpt.get().getComplejoDeportivo().getId_complejo());
            model.addAttribute("tabActiva", tab);
            return "admin-complejo/espacios/gestionar";
        }
        
        // Si no es cancha, intentar como Salón
        Optional<Salon> salonOpt = servicioSalon.obtenerPorId(id);
        if (salonOpt.isPresent()) {
            model.addAttribute("espacio", salonOpt.get());
            model.addAttribute("tipoEspacio", "Salón");
            model.addAttribute("esCancha", false);
            model.addAttribute("idComplejo", salonOpt.get().getComplejoDeportivo().getId_complejo());
            // Si es salón, forzar tab a políticas porque no tiene deportes
            model.addAttribute("tabActiva", "politicas-senia");
            return "admin-complejo/espacios/gestionar";
        }
        
        // No encontrado
        redirectAttributes.addFlashAttribute("error", "Espacio no encontrado.");
        return "redirect:/admin-complejo/espacios/listar";
    }

    /**
     * Fragment AJAX: Cargar formulario de deportes para una cancha
     */
    @GetMapping("/gestionar/{id}/deportes")
    public String cargarDeportes(@PathVariable("id") Long id,
                                 Model model,
                                 HttpServletRequest request) {
        
        Optional<Cancha> canchaOpt = servicioCancha.obtenerPorId(id);
        if (!canchaOpt.isPresent()) {
            model.addAttribute("error", "Cancha no encontrada.");
            return "admin-complejo/fragments/error-fragment :: error-content";
        }

        Cancha cancha = canchaOpt.get();
        
        // Obtener todos los deportes disponibles (primero intentar solo activos, si no hay, traer todos)
        List<Deporte> deportesDisponibles = servicioDeporte.listarActivos();
        System.out.println("=== DEBUG: Deportes activos encontrados: " + deportesDisponibles.size());
        
        // Si no hay deportes activos, traer todos
        if (deportesDisponibles.isEmpty()) {
            deportesDisponibles = servicioDeporte.listarTodos();
            System.out.println("=== DEBUG: Deportes totales (todos): " + deportesDisponibles.size());
        }
        
        // Obtener deportes ya asignados a esta cancha
        List<CanchaDeporte> deportesAsignados = servicioCanchaDeporte.obtenerDeportesPorCancha(id);
        List<Long> idsDeportesAsignados = deportesAsignados.stream()
                .map(cd -> cd.getDeporte().getId())
                .collect(Collectors.toList());
        
        System.out.println("=== DEBUG: Deportes asignados a la cancha: " + idsDeportesAsignados.size());

        model.addAttribute("cancha", cancha);
        model.addAttribute("deportesDisponibles", deportesDisponibles);
        model.addAttribute("deportesAsignados", idsDeportesAsignados);

        // Si es petición AJAX, devolver solo el fragmento
        String requestedWithHeader = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWithHeader)) {
            return "admin-complejo/fragments/deportes-fragment :: deportes-tab";
        }

        return "redirect:/admin-complejo/espacios/gestionar/" + id + "?tab=deportes";
    }

    /**
     * POST: Guardar deportes asignados a una cancha
     */
    @PostMapping("/gestionar/{id}/deportes/guardar")
    public String guardarDeportes(@PathVariable("id") Long id,
                                  @RequestParam(value = "deporteIds", required = false) List<Long> deporteIds,
                                  RedirectAttributes redirectAttributes) {
        
        try {
            servicioCanchaDeporte.asignarDeportesACancha(id, deporteIds);
            redirectAttributes.addFlashAttribute("exito", "Deportes asignados correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al asignar deportes: " + e.getMessage());
        }

        return "redirect:/admin-complejo/espacios/gestionar/" + id + "?tab=deportes";
    }

    /**
     * Fragment AJAX: Cargar formulario de política de seña
     */
    @GetMapping("/gestionar/{id}/politicas-senia")
    public String cargarPoliticasSenia(@PathVariable("id") Long id,
                                       Model model,
                                       HttpServletRequest request) {
        
        // Obtener el espacio (puede ser Cancha o Salón)
        Optional<EspacioReservable> espacioOpt = servicioEspacioReservable.obtenerPorId(id);
        if (!espacioOpt.isPresent()) {
            model.addAttribute("error", "Espacio no encontrado.");
            return "admin-complejo/fragments/error-fragment :: error-content";
        }

        EspacioReservable espacio = espacioOpt.get();
        
        // Obtener políticas disponibles del complejo (solo activas y vigentes)
        Long complejoId = espacio.getComplejoDeportivo().getId_complejo();
        List<PoliticaSenia> politicasDisponibles = servicioPoliticaSenia.listarPoliticasSeniaPorComplejoId(complejoId);
        
        // Obtener política actualmente asignada al espacio
        PoliticaSenia politicaAsignada = espacio.getPoliticaSenia();
        Long politicaAsignadaId = politicaAsignada != null ? politicaAsignada.getId() : null;

        model.addAttribute("espacio", espacio);
        model.addAttribute("politicasDisponibles", politicasDisponibles);
        model.addAttribute("politicaAsignadaId", politicaAsignadaId);

        // Si es petición AJAX, devolver solo el fragmento
        String requestedWithHeader = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWithHeader)) {
            return "admin-complejo/fragments/politicas-senia-fragment :: politicas-senia-tab";
        }

        return "redirect:/admin-complejo/espacios/gestionar/" + id + "?tab=politicas-senia";
    }

    /**
     * POST: Guardar política de seña asignada a un espacio
     */
    @PostMapping("/gestionar/{id}/politicas-senia/guardar")
    public String guardarPoliticaSenia(@PathVariable("id") Long id,
                                       @RequestParam("politicaId") Long politicaId,
                                       RedirectAttributes redirectAttributes) {
        
        try {
            EspacioReservable espacio = servicioEspacioReservable.obtenerPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Espacio no encontrado"));

            PoliticaSenia politica = servicioPoliticaSenia.obtenerPoliticaSeniaPorId(politicaId);
            if (politica == null) {
                throw new IllegalArgumentException("Política de seña no encontrada");
            }

            // Validación 1: Política debe estar activa
            if (!politica.getActivo()) {
                throw new IllegalStateException("La política seleccionada está inactiva");
            }

            // Validación 2: Política debe estar vigente HOY
            LocalDate hoy = LocalDate.now();
            if (hoy.isBefore(politica.getFechaInicioVigencia())) {
                throw new IllegalStateException("La política aún no está vigente");
            }
            if (hoy.isAfter(politica.getFechaFinVigencia())) {
                throw new IllegalStateException("La política ya no está vigente");
            }

            // Validación 3: Política debe pertenecer al mismo complejo del espacio
            if (!politica.getComplejoDeportivo().getId_complejo().equals(espacio.getComplejoDeportivo().getId_complejo())) {
                throw new IllegalStateException("La política no pertenece al mismo complejo");
            }

            // Asignar la política al espacio
            espacio.setPoliticaSenia(politica);
            servicioEspacioReservable.guardar(espacio);

            redirectAttributes.addFlashAttribute("exito", "Política de seña asignada correctamente.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al asignar política de seña.");
        }

        return "redirect:/admin-complejo/espacios/gestionar/" + id + "?tab=politicas-senia";
    }

    /**
     * GET: Cargar fragmento de políticas de cancelación
     */
    @GetMapping("/gestionar/{id}/politicas-cancelacion")
    public String cargarPoliticasCancelacion(@PathVariable("id") Long id, 
                                             Model model, 
                                             HttpServletRequest request) {
        
        EspacioReservable espacio = servicioEspacioReservable.obtenerPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Espacio no encontrado"));

        Long complejoId = espacio.getComplejoDeportivo().getId_complejo();

        // Obtener todas las políticas de cancelación disponibles del complejo
        List<PoliticaCancelacion> politicasDisponibles = servicioPoliticaCancelacion.listarPoliticasCancelacionPorComplejoId(complejoId);

        // Obtener la política de cancelación actualmente asignada (si existe)
        Long politicaAsignadaId = espacio.getPoliticaCancelacion() != null ? espacio.getPoliticaCancelacion().getId() : null;

        model.addAttribute("espacio", espacio);
        model.addAttribute("politicasDisponibles", politicasDisponibles);
        model.addAttribute("politicaAsignadaId", politicaAsignadaId);

        // Si es petición AJAX, devolver solo el fragmento
        String requestedWithHeader = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWithHeader)) {
            return "admin-complejo/fragments/politicas-cancelacion-fragment :: politicas-cancelacion-tab";
        }

        return "redirect:/admin-complejo/espacios/gestionar/" + id + "?tab=politicas-cancelacion";
    }

    /**
     * POST: Guardar política de cancelación asignada a un espacio
     */
    @PostMapping("/gestionar/{id}/politicas-cancelacion/guardar")
    public String guardarPoliticaCancelacion(@PathVariable("id") Long id,
                                            @RequestParam("politicaId") Long politicaId,
                                            RedirectAttributes redirectAttributes) {
        
        try {
            EspacioReservable espacio = servicioEspacioReservable.obtenerPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Espacio no encontrado"));

            PoliticaCancelacion politica = servicioPoliticaCancelacion.obtenerPoliticaCancelacionPorId(politicaId);
            if (politica == null) {
                throw new IllegalArgumentException("Política de cancelación no encontrada");
            }

            // Validación 1: Política debe estar activa
            if (!politica.getActivo()) {
                throw new IllegalStateException("La política seleccionada está inactiva");
            }

            // Validación 2: Política debe estar vigente HOY
            LocalDate hoy = LocalDate.now();
            if (hoy.isBefore(politica.getFechaInicioVigencia())) {
                throw new IllegalStateException("La política aún no está vigente");
            }
            if (hoy.isAfter(politica.getFechaFinVigencia())) {
                throw new IllegalStateException("La política ya no está vigente");
            }

            // Validación 3: Política debe pertenecer al mismo complejo del espacio
            if (!politica.getComplejoDeportivo().getId_complejo().equals(espacio.getComplejoDeportivo().getId_complejo())) {
                throw new IllegalStateException("La política no pertenece al mismo complejo");
            }

            // Asignar la política al espacio
            espacio.setPoliticaCancelacion(politica);
            servicioEspacioReservable.guardar(espacio);

            redirectAttributes.addFlashAttribute("exito", "Política de cancelación asignada correctamente.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al asignar política de cancelación.");
        }

        return "redirect:/admin-complejo/espacios/gestionar/" + id + "?tab=politicas-cancelacion";
    }
}
