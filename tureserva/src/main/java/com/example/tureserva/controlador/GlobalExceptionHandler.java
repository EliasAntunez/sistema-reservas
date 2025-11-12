package com.example.tureserva.controlador;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.ConstraintViolationException;

/**
 * Manejador global de excepciones para toda la aplicación.
 * Captura excepciones comunes y las convierte en mensajes amigables para el usuario.
 * 
 * <p>Esto mejora la UX evitando que se muestren stacktraces al usuario
 * y proporciona feedback claro sobre qué salió mal.
 * 
 * @author TuReserva
 * @version 1.0
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Maneja violaciones de constraints de validación (Bean Validation).
     * Ejemplo: cuando se intenta guardar un objeto con campos @NotNull nulos.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleConstraintViolation(ConstraintViolationException ex, 
                                            RedirectAttributes redirectAttributes) {
        log.error("Error de validación de constraints: {}", ex.getMessage());
        
        // Extraer el primer mensaje de error
        String mensaje = ex.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getMessage())
            .orElse("Error de validación en los datos ingresados");
        
        redirectAttributes.addFlashAttribute("error", mensaje);
        return "redirect:/";
    }
    
    /**
     * Maneja violaciones de integridad de datos en la base de datos.
     * Ejemplo: insertar un registro que viola una constraint UNIQUE.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                               RedirectAttributes redirectAttributes) {
        log.error("Error de integridad de datos: {}", ex.getMessage());
        
        String mensaje = "Error al guardar: el registro ya existe o viola restricciones de la base de datos";
        
        // Intentar extraer información más específica del mensaje de error
        String errorMessage = ex.getMessage();
        if (errorMessage != null) {
            if (errorMessage.contains("uk_config_nombre_complejo")) {
                mensaje = "Ya existe una configuración de horario con ese nombre en este complejo";
            } else if (errorMessage.contains("uk_espacio_nombre_complejo")) {
                mensaje = "Ya existe un espacio con ese nombre en este complejo";
            } else if (errorMessage.contains("uk_rango_config_dia_horas")) {
                mensaje = "Ya existe un rango con ese horario para ese día";
            }
        }
        
        redirectAttributes.addFlashAttribute("error", mensaje);
        return "redirect:/";
    }
    
    /**
     * Maneja argumentos ilegales (errores de lógica de negocio).
     * Ejemplo: cuando un servicio lanza IllegalArgumentException con un mensaje descriptivo.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException ex,
                                        RedirectAttributes redirectAttributes) {
        log.warn("Argumento ilegal: {}", ex.getMessage());
        
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/";
    }
    
    /**
     * Maneja errores de estado ilegal (operaciones no permitidas).
     * Ejemplo: intentar eliminar una configuración que está en uso.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalState(IllegalStateException ex,
                                     RedirectAttributes redirectAttributes) {
        log.warn("Estado ilegal: {}", ex.getMessage());
        
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/";
    }
    
    /**
     * Maneja cualquier excepción no capturada específicamente.
     * Último recurso para evitar mostrar stacktraces al usuario.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex,
                                         RedirectAttributes redirectAttributes) {
        log.error("Error inesperado: {}", ex.getMessage(), ex);
        
        redirectAttributes.addFlashAttribute("error", 
            "Ha ocurrido un error inesperado. Por favor, intenta nuevamente o contacta al administrador.");
        return "redirect:/";
    }
}
