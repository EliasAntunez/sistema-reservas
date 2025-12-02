package com.example.tureserva.controlador;

import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.ServicioReportePdf;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioAdministradorComplejo;
import com.example.tureserva.servicio.ServicioUsuarioUnificado;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.AdministradorComplejo;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.tureserva.servicio.dto.ReporteFinancieroDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reportes/financiero")
public class ControladorReporte {

    private final ServicioReportePdf servicioReportePdf;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;
    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    private final ServicioUsuarioUnificado servicioUsuarioUnificado;
    private final com.example.tureserva.repositorio.RepositorioPago repositorioPago;

    public ControladorReporte(RepositorioReserva repositorioReserva,
                             ServicioReportePdf servicioReportePdf,
                             ServicioComplejoDeportivo servicioComplejoDeportivo,
                             ServicioAdministradorComplejo servicioAdministradorComplejo,
                             ServicioUsuarioUnificado servicioUsuarioUnificado,
                             com.example.tureserva.repositorio.RepositorioPago repositorioPago) {
        this.servicioReportePdf = servicioReportePdf;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
        this.servicioUsuarioUnificado = servicioUsuarioUnificado;
        this.repositorioPago = repositorioPago;
    }

    private final Logger logger = LoggerFactory.getLogger(ControladorReporte.class);

    @GetMapping
        public String mostrarReporte(
            @RequestParam(value = "inicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(value = "fin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(value = "complejoId", required = false) Long complejoId,
            Model model,
            Authentication authentication) {

        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();

        // Obtener lista de complejos disponibles para el usuario autenticado
        List<ComplejoDeportivo> complejos;
        boolean esAdminComplejo = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN_COMPLEJO".equals(a.getAuthority()));

        if (esAdminComplejo) {
            String email = servicioUsuarioUnificado.obtenerEmail(authentication);
            AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorEmail(email);
            if (admin != null) {
                complejos = servicioComplejoDeportivo.obtenerComplejosPorAdministradorYActivoTrue(admin.getId());
                // Si se pasó un complejoId que no pertenece al admin, ignorarlo (mostrar todos)
                Long selectedId = complejoId;
                if (selectedId != null && complejos.stream().noneMatch(c -> c.getId_complejo().equals(selectedId))) {
                    complejoId = null;
                }
            } else {
                complejos = servicioComplejoDeportivo.obtenerComplejosActivos();
            }
        } else {
            // Usuarios no-admin ven todos los complejos activos
            complejos = servicioComplejoDeportivo.obtenerComplejosActivos();
        }

        try {
            // Obtener datos del reporte basado en PAGOS reales (no en estado de reserva)
            List<ReporteFinancieroDTO> datos = repositorioPago.obtenerReporteFinancieroPorPagos(inicio, fin, complejoId);
            model.addAttribute("datos", datos);
        } catch (Exception ex) {
            logger.error("Error obteniendo reporte financiero para inicio={} fin={} complejoId={}: {}",
                    inicio, fin, complejoId, ex.getMessage(), ex);
            model.addAttribute("datos", java.util.Collections.emptyList());
            model.addAttribute("error", "Ocurrió un error al generar el reporte. Revisa los logs del servidor.");
            return "reportes/financiero";
        }
        model.addAttribute("inicio", inicio);
        model.addAttribute("fin", fin);
        model.addAttribute("complejoId", complejoId);
        model.addAttribute("complejos", complejos);

        return "reportes/financiero";
    }

    @GetMapping("/pdf")
        public void descargarPdf(
            @RequestParam(value = "inicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(value = "fin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(value = "complejoId", required = false) Long complejoId,
            HttpServletResponse response,
            Authentication authentication) {

        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();

        // Si es admin, verificar que el complejoId (si existe) pertenezca al admin
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN_COMPLEJO".equals(a.getAuthority()))) {
            String email = servicioUsuarioUnificado.obtenerEmail(authentication);
            AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorEmail(email);
            if (admin != null && complejoId != null) {
                Long selectedId = complejoId;
                boolean pertenece = servicioComplejoDeportivo.obtenerComplejosPorAdministradorYActivoTrue(admin.getId())
                        .stream().anyMatch(c -> c.getId_complejo().equals(selectedId));
                if (!pertenece) {
                    complejoId = null; // ignorar filtro externo
                }
            }
        }

        try {
            // Obtener datos del reporte basado en PAGOS reales (no en estado de reserva)
            List<ReporteFinancieroDTO> datos = repositorioPago.obtenerReporteFinancieroPorPagos(inicio, fin, complejoId);

            // Determinar nombre del complejo para el encabezado
            String nombreComplejo = "Todos";
            if (complejoId != null) {
                nombreComplejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                        .map(c -> c.getNombre_complejo())
                        .orElse("-");
            }

            // Quién generó el reporte
            String generadoPor = servicioUsuarioUnificado.obtenerNombreParaMostrar(authentication);

            servicioReportePdf.generarReporteFinancieroPdf(datos, response, generadoPor, nombreComplejo, inicio, fin);
        } catch (Exception ex) {
            logger.error("Error generando PDF de reporte financiero: {}", ex.getMessage(), ex);
            try {
                response.reset();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generando PDF");
            } catch (Exception ignored) {}
        }
    }
}
