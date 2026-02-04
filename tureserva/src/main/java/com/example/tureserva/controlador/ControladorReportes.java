package com.example.tureserva.controlador;

import com.example.tureserva.servicio.ServicioReporte;
import com.example.tureserva.servicio.ServicioReportePdf;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioValidacionPermisos;
import com.example.tureserva.servicio.dto.OcupacionMatrixDTO;
import com.example.tureserva.modelo.ComplejoDeportivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
public class ControladorReportes {
    
    private static final Logger logger = LoggerFactory.getLogger(ControladorReportes.class);
    
    private final ServicioReporte servicioReporte;
    private final ServicioReportePdf servicioPdf;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;
    private final ServicioValidacionPermisos servicioValidacionPermisos;

    public ControladorReportes(ServicioReporte servicioReporte, 
                               ServicioReportePdf servicioPdf,
                               ServicioComplejoDeportivo servicioComplejoDeportivo,
                               ServicioValidacionPermisos servicioValidacionPermisos) {
        this.servicioReporte = servicioReporte;
        this.servicioPdf = servicioPdf;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
        this.servicioValidacionPermisos = servicioValidacionPermisos;
    }

    @GetMapping("/reportes/ocupacion")
    public String mostrarOcupacion(
            @RequestParam(required = false) Long complejoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Integer horaInicio,
            @RequestParam(required = false) Integer horaFin,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();
        
        // VALIDACIÓN DE FECHAS: inicio debe ser menor o igual que fin
        if (inicio.isAfter(fin)) {
            redirectAttributes.addFlashAttribute("error", "La fecha de inicio no puede ser posterior a la fecha fin");
            redirectAttributes.addAttribute("complejoId", complejoId);
            return "redirect:/reportes/ocupacion";
        }

        // VALIDACIÓN DE PERMISOS usando servicio centralizado
        if (!servicioValidacionPermisos.tienePermisoSobreComplejo(authentication, complejoId)) {
            servicioValidacionPermisos.registrarIntentoNoAutorizado(authentication, "reporte de ocupación", complejoId);
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para ver reportes de ese complejo");
            return servicioValidacionPermisos.esAdminComplejo(authentication) 
                ? "redirect:/admin-complejo/dashboard" 
                : "redirect:/dashboard";
        }
        
        // Obtener lista de complejos permitidos para el filtro
        List<ComplejoDeportivo> complejosPermitidos = servicioValidacionPermisos.obtenerComplejosPermitidos(authentication);
        
        if (complejosPermitidos.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No tienes complejos asignados");
            return "redirect:/dashboard";
        }

        try {
            OcupacionMatrixDTO dto = servicioReporte.obtenerOcupacionMatrix(complejoId, inicio, fin, complejosPermitidos, horaInicio, horaFin);
            
            model.addAttribute("complejos", complejosPermitidos);
            model.addAttribute("horas", dto.getHoras());
            model.addAttribute("matrix", dto.getMatrix());
            model.addAttribute("maxCount", dto.getMaxCount());
            model.addAttribute("totalesPorDia", dto.getTotalesPorDia());
            model.addAttribute("totalesPorHora", dto.getTotalesPorHora());
            model.addAttribute("totalGeneral", dto.getTotalGeneral());
            model.addAttribute("analisisHorarios", dto.getAnalisisHorarios());
            model.addAttribute("nombresColumnas", dto.getNombresColumnas());
            model.addAttribute("tipoVista", dto.getTipoVista());
            model.addAttribute("inicio", inicio);
            model.addAttribute("fin", fin);
            model.addAttribute("complejoId", complejoId);
            model.addAttribute("horaInicio", horaInicio);
            model.addAttribute("horaFin", horaFin);
            
            return "reportes/ocupacion";
            
        } catch (Exception e) {
            logger.error("Error al generar reporte de ocupación: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Ocurrió un error al generar el reporte. Inténtalo nuevamente.");
            return servicioValidacionPermisos.esAdminComplejo(authentication) 
                ? "redirect:/admin-complejo/dashboard" 
                : "redirect:/dashboard";
        }
    }

    @GetMapping("/reportes/ocupacion/pdf")
    public void descargarOcupacionPdf(
            @RequestParam(required = false) Long complejoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Integer horaInicio,
            @RequestParam(required = false) Integer horaFin,
            Authentication authentication,
            HttpServletResponse response) {

        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();
        
        // VALIDACIÓN DE FECHAS: inicio debe ser menor o igual que fin
        if (inicio.isAfter(fin)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // VALIDACIÓN DE PERMISOS antes de generar PDF usando servicio centralizado
        if (!servicioValidacionPermisos.tienePermisoSobreComplejo(authentication, complejoId)) {
            servicioValidacionPermisos.registrarIntentoNoAutorizado(authentication, "descarga de PDF de ocupación", complejoId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        // Obtener lista de complejos permitidos para cálculo de horarios
        List<ComplejoDeportivo> complejosPermitidos = servicioValidacionPermisos.obtenerComplejosPermitidos(authentication);

        try {
            OcupacionMatrixDTO dto = servicioReporte.obtenerOcupacionMatrix(complejoId, inicio, fin, complejosPermitidos, horaInicio, horaFin);
            
            // Obtener información del complejo y usuario para el PDF
            String nombreComplejo = null;
            if (complejoId != null) {
                Optional<ComplejoDeportivo> complejoOpt = servicioComplejoDeportivo.obtenerPorId(complejoId);
                nombreComplejo = complejoOpt.map(ComplejoDeportivo::getNombre_complejo).orElse(null);
            }
            
            String generadoPor = authentication != null ? authentication.getName() : "Sistema";
            
            servicioPdf.generarReporteOcupacionPdf(dto.getHoras(), dto.getMatrix(), response, 
                nombreComplejo, generadoPor, inicio, fin);
                
        } catch (Exception e) {
            logger.error("Error al generar PDF de ocupación: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
