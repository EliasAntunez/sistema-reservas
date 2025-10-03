package com.example.tureserva.utiles;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilidades para manejo de autenticación
 */
public class UtilesAutenticacion {
    
    /**
     * Verifica si el usuario actual está autenticado
     * @return true si está autenticado, false si no
     */
    public static boolean usuarioEstaAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser");
    }
    
    /**
     * Obtiene el nombre del usuario autenticado
     * @return nombre del usuario o null si no está autenticado
     */
    public static String obtenerNombreUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (usuarioEstaAutenticado()) {
            return auth.getName();
        }
        return null;
    }
}