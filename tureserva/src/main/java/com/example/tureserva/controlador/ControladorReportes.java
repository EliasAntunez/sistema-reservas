package com.example.tureserva.controlador;

import com.example.tureserva.servicio.ServicioReporte;
import com.example.tureserva.servicio.ServicioReportePdf;
import com.example.tureserva.servicio.dto.OcupacionMatrixDTO;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

@Controller
public class ControladorReportes {
    private final ServicioReporte servicioReporte;
    private final ServicioReportePdf servicioPdf;
    private final RepositorioComplejoDeportivo repositorioComplejo;

    public ControladorReportes(ServicioReporte servicioReporte, ServicioReportePdf servicioPdf,
                               RepositorioComplejoDeportivo repositorioComplejo) {
        this.servicioReporte = servicioReporte;
        this.servicioPdf = servicioPdf;
        this.repositorioComplejo = repositorioComplejo;
    }

    @GetMapping("/reportes/ocupacion")
    public String mostrarOcupacion(
            @RequestParam(required = false) Long complejoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            Model model) {

        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();

        OcupacionMatrixDTO dto = servicioReporte.obtenerOcupacionMatrix(complejoId, inicio, fin);
        // lista de complejos para el filtro
        java.util.List<ComplejoDeportivo> complejos = repositorioComplejo.findByActivoTrue();
        model.addAttribute("complejos", complejos);
        model.addAttribute("horas", dto.getHoras());
        model.addAttribute("matrix", dto.getMatrix());
        model.addAttribute("maxCount", dto.getMaxCount());
        model.addAttribute("totalesPorDia", dto.getTotalesPorDia());
        model.addAttribute("totalGeneral", dto.getTotalGeneral());
        model.addAttribute("inicio", inicio);
        model.addAttribute("fin", fin);
        model.addAttribute("complejoId", complejoId);

        return "reportes/ocupacion";
    }

    @GetMapping("/reportes/ocupacion/pdf")
    public void descargarOcupacionPdf(
            @RequestParam(required = false) Long complejoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {

        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();

        OcupacionMatrixDTO dto = servicioReporte.obtenerOcupacionMatrix(complejoId, inicio, fin);
        // nombreComplejo y generadoPor podrían obtenerse del usuario autenticado o repositorio; por ahora se dejan nulos
        servicioPdf.generarReporteOcupacionPdf(dto.getHoras(), dto.getMatrix(), response, null, null, inicio, fin);
    }
}
