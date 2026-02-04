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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/reportes/financiero")
public class ControladorReporte {

    private static final Logger logger = LoggerFactory.getLogger(ControladorReporte.class);

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
        
        // Validación: inicio no puede ser posterior a fin
        if (inicio.isAfter(fin)) {
            model.addAttribute("error", "La fecha de inicio no puede ser posterior a la fecha de fin.");
            model.addAttribute("datos", java.util.Collections.emptyList());
            model.addAttribute("inicio", inicio);
            model.addAttribute("fin", fin);
            model.addAttribute("complejos", java.util.Collections.emptyList());
            return "reportes/financiero";
        }

        // Obtener lista de complejos disponibles para el usuario autenticado
        List<ComplejoDeportivo> complejos;
        boolean esAdminComplejo = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN_COMPLEJO".equals(a.getAuthority()));

        if (esAdminComplejo) {
            String email = servicioUsuarioUnificado.obtenerEmail(authentication);
            AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorEmail(email);
            if (admin != null) {
                complejos = servicioComplejoDeportivo.obtenerComplejosPorAdministradorYActivoTrue(admin.getId());
                // Si se pasó un complejoId que no pertenece al admin, ignorarlo
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
            // USAR REPORTE TEMPORAL (agrupado por fecha) para cumplir requisito académico: eje X = tiempo
            List<com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO> datos;
            
            // Si es admin de complejo y NO seleccionó complejo específico, mostrar TODOS sus complejos
            if (esAdminComplejo && complejoId == null && !complejos.isEmpty()) {
                List<Long> idsComplejos = complejos.stream()
                    .map(ComplejoDeportivo::getId_complejo)
                    .toList();
                datos = repositorioPago.obtenerReporteFinancieroTemporalMultiplesComplejos(inicio, fin, idsComplejos);
            } else if (complejoId != null) {
                // Seleccionó un complejo específico
                datos = repositorioPago.obtenerReporteFinancieroTemporal(inicio, fin, complejoId);
            } else {
                // Usuario no-admin o admin sin complejos: ver todos
                datos = repositorioPago.obtenerReporteFinancieroTemporal(inicio, fin, null);
            }
            
            // Calcular métricas agregadas
            java.math.BigDecimal totalIngresos = datos.stream()
                .map(d -> d.getIngresos() != null ? d.getIngresos() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            
            Long totalReservas = datos.stream()
                .mapToLong(d -> d.getCantidadReservas() != null ? d.getCantidadReservas() : 0L)
                .sum();
            
            long diasConDatos = datos.size();
            long diasPeriodo = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
            
            java.math.BigDecimal promedioIngresoDiario = diasConDatos > 0 
                ? totalIngresos.divide(java.math.BigDecimal.valueOf(diasConDatos), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;
            
            java.math.BigDecimal ticketPromedio = totalReservas > 0
                ? totalIngresos.divide(java.math.BigDecimal.valueOf(totalReservas), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;
            
            model.addAttribute("datos", datos);
            model.addAttribute("totalIngresos", totalIngresos);
            model.addAttribute("totalReservas", totalReservas);
            model.addAttribute("promedioIngresoDiario", promedioIngresoDiario);
            model.addAttribute("ticketPromedio", ticketPromedio);
            model.addAttribute("diasConDatos", diasConDatos);
            model.addAttribute("diasPeriodo", diasPeriodo);
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

        // Validación: inicio no puede ser posterior a fin
        if (inicio.isAfter(fin)) {
            try {
                response.reset();
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "La fecha de inicio no puede ser posterior a la fecha de fin.");
            } catch (Exception ignored) {}
            return;
        }

        // Determinar si es admin de complejo y obtener sus complejos
        boolean esAdminComplejo = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN_COMPLEJO".equals(a.getAuthority()));
        List<ComplejoDeportivo> complejosAdmin = null;
        
        if (esAdminComplejo) {
            String email = servicioUsuarioUnificado.obtenerEmail(authentication);
            AdministradorComplejo admin = servicioAdministradorComplejo.obtenerAdministradorPorEmail(email);
            if (admin != null) {
                complejosAdmin = servicioComplejoDeportivo.obtenerComplejosPorAdministradorYActivoTrue(admin.getId());
                
                // Validar si el complejoId pertenece al admin
                if (complejoId != null) {
                    Long selectedId = complejoId;
                    boolean pertenece = complejosAdmin.stream().anyMatch(c -> c.getId_complejo().equals(selectedId));
                    if (!pertenece) {
                        complejoId = null; // ignorar filtro externo
                    }
                }
            }
        }

        try {
            // USAR REPORTE TEMPORAL (agrupado por fecha) para cumplir requisito académico: eje X = tiempo
            List<com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO> datos;
            
            // Si es admin de complejo y NO seleccionó complejo específico, mostrar TODOS sus complejos
            if (esAdminComplejo && complejoId == null && complejosAdmin != null && !complejosAdmin.isEmpty()) {
                List<Long> idsComplejos = complejosAdmin.stream()
                    .map(ComplejoDeportivo::getId_complejo)
                    .toList();
                datos = repositorioPago.obtenerReporteFinancieroTemporalMultiplesComplejos(inicio, fin, idsComplejos);
            } else if (complejoId != null) {
                // Seleccionó un complejo específico
                datos = repositorioPago.obtenerReporteFinancieroTemporal(inicio, fin, complejoId);
            } else {
                // Usuario no-admin o admin sin complejos: ver todos
                datos = repositorioPago.obtenerReporteFinancieroTemporal(inicio, fin, null);
            }

            // Determinar nombre del complejo para el encabezado
            String nombreComplejo = "Todos";
            if (complejoId != null) {
                nombreComplejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                        .map(c -> c.getNombre_complejo())
                        .orElse("-");
            }

            // Quién generó el reporte
            String generadoPor = servicioUsuarioUnificado.obtenerNombreParaMostrar(authentication);

            // Calcular métricas KPI para incluir en el PDF
            BigDecimal totalIngresos = datos.stream()
                    .map(d -> d.getIngresos() != null ? d.getIngresos() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Long totalReservas = datos.stream()
                    .mapToLong(d -> d.getCantidadReservas() != null ? d.getCantidadReservas() : 0L)
                    .sum();

            long diasConDatos = datos.stream()
                    .filter(d -> d.getCantidadReservas() != null && d.getCantidadReservas() > 0)
                    .count();

            long diasPeriodo = ChronoUnit.DAYS.between(inicio, fin) + 1;

            BigDecimal promedioIngresoDiario = diasConDatos > 0
                    ? totalIngresos.divide(BigDecimal.valueOf(diasConDatos), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal ticketPromedio = totalReservas > 0
                    ? totalIngresos.divide(BigDecimal.valueOf(totalReservas), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            servicioReportePdf.generarReporteFinancieroPdf(datos, response, generadoPor, nombreComplejo, inicio, fin,
                    totalIngresos, totalReservas, promedioIngresoDiario, ticketPromedio, diasConDatos, diasPeriodo);
        } catch (Exception ex) {
            logger.error("Error generando PDF de reporte financiero: {}", ex.getMessage(), ex);
            try {
                response.reset();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generando PDF");
            } catch (Exception ignored) {}
        }
    }
}
