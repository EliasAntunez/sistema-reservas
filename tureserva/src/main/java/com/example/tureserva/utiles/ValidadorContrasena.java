package com.example.tureserva.utiles;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Clase utilitaria para validaciones comunes de contraseñas
 */
public class ValidadorContrasena {

    /**
     * Valida el cambio de contraseña con todas las validaciones necesarias
     * @param contrasenaActual Contraseña actual
     * @param nuevaContrasena Nueva contraseña
     * @param confirmarContrasena Confirmación de la nueva contraseña
     * @param model Model para errores inmediatos
     * @param redirectAttributes RedirectAttributes para errores de redirección
     * @param vistaError Vista a retornar en caso de error
     * @return null si las validaciones pasan, la vista de error si fallan
     */
    public static String validarCambioContrasena(String contrasenaActual, String nuevaContrasena, 
                                               String confirmarContrasena, Model model, 
                                               RedirectAttributes redirectAttributes, String vistaError) {
        
        // Validar contraseña actual
        if (esVacio(contrasenaActual)) {
            return manejarError("La contraseña actual es obligatoria", model, redirectAttributes, vistaError);
        }

        // Validar nueva contraseña
        if (esVacio(nuevaContrasena)) {
            return manejarError("La nueva contraseña es obligatoria", model, redirectAttributes, vistaError);
        }

        // Validar longitud mínima (nuevaContrasena ya no es null aquí)
        if (nuevaContrasena.length() < 6) {
            return manejarError("La nueva contraseña debe tener al menos 6 caracteres", model, redirectAttributes, vistaError);
        }

        // Validar que las contraseñas coincidan
        if (!ValidadorFormulario.validarContrasenasCoindicen(nuevaContrasena, confirmarContrasena)) {
            return manejarError("Las contraseñas no coinciden", model, redirectAttributes, vistaError);
        }

        return null; // Todas las validaciones pasaron
    }

    /**
     * Verifica si una cadena es null o está vacía
     */
    private static boolean esVacio(String cadena) {
        return cadena == null || cadena.trim().isEmpty();
    }

    /**
     * Maneja el error agregando el mensaje apropiado y retornando la vista correspondiente
     */
    private static String manejarError(String mensaje, Model model, RedirectAttributes redirectAttributes, String vistaError) {
        if (model != null) {
            ManejadorMensajes.agregarMensajeError(model, mensaje);
            return vistaError;
        } else if (redirectAttributes != null) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, mensaje);
            return "redirect:" + vistaError;
        }
        return vistaError;
    }
}