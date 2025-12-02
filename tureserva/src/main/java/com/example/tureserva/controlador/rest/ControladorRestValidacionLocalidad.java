package com.example.tureserva.controlador.rest;

import com.example.tureserva.servicio.ServicioValidacionLocalidad;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * API REST para validar localidades basándose en coordenadas geográficas.
 * Usado por el frontend para validación en tiempo real al seleccionar direcciones.
 */
@RestController
@RequestMapping("/api/validacion-localidad")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControladorRestValidacionLocalidad {
    
    private final ServicioValidacionLocalidad servicioValidacionLocalidad;
    
    public ControladorRestValidacionLocalidad(ServicioValidacionLocalidad servicioValidacionLocalidad) {
        this.servicioValidacionLocalidad = servicioValidacionLocalidad;
    }
    
    /**
     * Valida si unas coordenadas están dentro del área de cobertura.
     * 
     * Ejemplo de uso:
     * POST /api/validacion-localidad/validar
     * Body: {"latitud": -27.9097, "longitud": -55.7581}
     * 
     * Respuesta exitosa (200):
     * {
     *   "valido": true,
     *   "localidadId": 1,
     *   "mensaje": "Ubicación válida en Apóstoles (2.5 km del centro)",
     *   "distanciaKm": 2.5
     * }
     * 
     * Respuesta fallida (200 con valido=false):
     * {
     *   "valido": false,
     *   "localidadId": null,
     *   "mensaje": "Lo sentimos, actualmente solo operamos en Apóstoles...",
     *   "distanciaKm": 85.3
     * }
     */
    @PostMapping("/validar")
    public ResponseEntity<Map<String, Object>> validarCobertura(@RequestBody Map<String, String> coordenadas) {
        try {
            BigDecimal latitud = new BigDecimal(coordenadas.get("latitud"));
            BigDecimal longitud = new BigDecimal(coordenadas.get("longitud"));
            
            var resultado = servicioValidacionLocalidad.validarCobertura(latitud, longitud);
            
            return ResponseEntity.ok(Map.of(
                "valido", resultado.isValido(),
                "localidadId", resultado.getLocalidadId() != null ? resultado.getLocalidadId() : "",
                "nombreLocalidad", resultado.getNombreLocalidad() != null ? resultado.getNombreLocalidad() : "",
                "mensaje", resultado.getMensaje(),
                "distanciaKm", resultado.getDistanciaKm()
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "valido", false,
                "mensaje", "Coordenadas inválidas"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "valido", false,
                "mensaje", "Error al validar cobertura: " + e.getMessage()
            ));
        }
    }
}
