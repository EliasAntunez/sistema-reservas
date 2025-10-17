package com.example.tureserva.controlador;

import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/geografico")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControladorGeograficoApi {

    private final ServicioComplejoDeportivo servicioComplejo;

    public ControladorGeograficoApi(ServicioComplejoDeportivo servicioComplejo) {
        this.servicioComplejo = servicioComplejo;
    }

    /**
     * Obtener provincias por país
     */
    @GetMapping("/provincias/{paisId}")
    public ResponseEntity<?> obtenerProvinciasPorPais(@PathVariable Long paisId) {
        try {
            return ResponseEntity.ok(servicioComplejo.obtenerProvinciasPorPais(paisId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener provincias: " + e.getMessage());
        }
    }

    /**
     * Obtener localidades por provincia
     */
    @GetMapping("/localidades/{provinciaId}")
    public ResponseEntity<?> obtenerLocalidadesPorProvincia(@PathVariable Long provinciaId) {
        try {
            return ResponseEntity.ok(servicioComplejo.obtenerLocalidadesPorProvincia(provinciaId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener localidades: " + e.getMessage());
        }
    }
}