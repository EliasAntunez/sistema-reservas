package com.example.tureserva.utiles;

import org.springframework.validation.BindingResult;

/**
 * Clase utilitaria para validaciones comunes en formularios
 */
public class ValidadorFormulario {

    /**
     * Valida los campos básicos de un usuario (nombre, apellido, email)
     */
    public static void validarCamposBasicosUsuario(String nombre, String apellido, String email, BindingResult bindingResult) {
        if (nombre == null || nombre.trim().isEmpty()) {
            bindingResult.rejectValue("nombre", "error.usuario", "El nombre es obligatorio");
        }
        if (apellido == null || apellido.trim().isEmpty()) {
            bindingResult.rejectValue("apellido", "error.usuario", "El apellido es obligatorio");
        }
        if (email == null || email.trim().isEmpty()) {
            bindingResult.rejectValue("email", "error.usuario", "El email es obligatorio");
        }
    }

    /**
     * Valida los campos completos de un usuario incluyendo DNI (nombre, apellido, email, dni)
     */
    public static void validarCamposCompletosUsuario(String nombre, String apellido, String email, String dni, BindingResult bindingResult) {
        validarCamposBasicosUsuario(nombre, apellido, email, bindingResult);
        validarDni(dni, bindingResult);
        
        // Validar formato de email
        if (email != null && !email.trim().isEmpty() && !validarFormatoEmail(email)) {
            bindingResult.rejectValue("email", "error.email", "El formato del email no es válido");
        }
    }

    /**
     * Valida una contraseña con criterios básicos
     */
    public static void validarContrasena(String contrasena, BindingResult bindingResult, String campo) {
        if (contrasena == null || contrasena.trim().isEmpty()) {
            bindingResult.rejectValue(campo, "error.contrasena", "La contraseña es obligatoria");
        } else if (contrasena.length() < 6) {
            bindingResult.rejectValue(campo, "error.contrasena", "La contraseña debe tener al menos 6 caracteres");
        }
    }

    /**
     * Valida que las contraseñas coincidan
     */
    public static boolean validarContrasenasCoindicen(String nuevaContrasena, String confirmarContrasena) {
        return nuevaContrasena != null && nuevaContrasena.equals(confirmarContrasena);
    }

    /**
     * Valida que una contraseña no esté vacía y tenga longitud mínima
     */
    public static boolean validarLongitudContrasena(String contrasena) {
        return contrasena != null && !contrasena.trim().isEmpty() && contrasena.length() >= 6;
    }

    /**
     * Valida formato básico de email
     */
    public static boolean validarFormatoEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    /**
     * Valida el formato del DNI (solo números)
     */
    public static boolean validarFormatoDni(String dni) {
        if (dni == null || dni.trim().isEmpty()) {
            return false; // DNI es obligatorio
        }
        // Verificar que solo contenga números y tenga entre 7 y 8 dígitos
        return dni.matches("^[0-9]{7,8}$");
    }

    /**
     * Valida el DNI con mensaje de error en el BindingResult
     */
    public static void validarDni(String dni, BindingResult bindingResult) {
        if (dni == null || dni.trim().isEmpty()) {
            bindingResult.rejectValue("dni", "error.dni", "El DNI es obligatorio");
        } else if (!validarFormatoDni(dni)) {
            bindingResult.rejectValue("dni", "error.dni", "El DNI debe contener solo números y tener entre 7 y 8 dígitos");
        }
    }
}