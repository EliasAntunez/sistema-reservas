package com.example.tureserva.utiles;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Clase utilitaria para manejo consistente de mensajes y errores
 */
public class ManejadorMensajes {

    /**
     * Agrega un mensaje de éxito al modelo
     */
    public static void agregarMensajeExito(RedirectAttributes redirectAttributes, String mensaje) {
        redirectAttributes.addFlashAttribute("mensaje", mensaje);
    }

    /**
     * Agrega un mensaje de error al modelo
     */
    public static void agregarMensajeError(Model model, String mensaje) {
        model.addAttribute("error", mensaje);
    }

    /**
     * Agrega un mensaje de error a RedirectAttributes
     */
    public static void agregarMensajeError(RedirectAttributes redirectAttributes, String mensaje) {
        redirectAttributes.addFlashAttribute("error", mensaje);
    }

    /**
     * Mensaje de perfil no encontrado
     */
    public static String PERFIL_NO_ENCONTRADO = "No se pudo cargar el perfil";

    /**
     * Mensaje de usuario no encontrado
     */
    public static String USUARIO_NO_ENCONTRADO = "No se pudo encontrar el usuario";

    /**
     * Mensaje de perfil actualizado
     */
    public static String PERFIL_ACTUALIZADO = "Perfil actualizado correctamente";

    /**
     * Mensaje de contraseña actualizada
     */
    public static String CONTRASENA_ACTUALIZADA = "Contraseña actualizada correctamente";

    /**
     * Mensaje de error genérico
     */
    public static String ERROR_GENERICO = "Error al procesar la solicitud. Inténtelo de nuevo.";

    /**
     * Mensaje de contraseña actual incorrecta
     */
    public static String CONTRASENA_ACTUAL_INCORRECTA = "La contraseña actual no es correcta";

    /**
     * Mensaje de email ya en uso
     */
    public static String EMAIL_EN_USO = "El email ya está en uso";
}