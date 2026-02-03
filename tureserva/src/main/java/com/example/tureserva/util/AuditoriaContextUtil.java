package com.example.tureserva.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utilidad para obtener contexto de auditoría desde el contexto de Spring.
 * 
 * <p>Proporciona acceso thread-safe a información de la solicitud HTTP actual
 * y del usuario autenticado para propósitos de auditoría.
 * 
 * @author TuReserva
 * @since Fase 2 - Arquitectura Basada en Eventos
 */
public class AuditoriaContextUtil {
    
    private AuditoriaContextUtil() {
        // Clase de utilidades - constructor privado
    }
    
    /**
     * Obtiene la dirección IP del cliente desde el request actual.
     * Maneja proxies y load balancers correctamente.
     * 
     * @return La dirección IP del cliente, o "SYSTEM" si no hay contexto HTTP
     */
    public static String obtenerIpAddress() {
        HttpServletRequest request = obtenerRequestActual();
        if (request == null) {
            return "SYSTEM";
        }
        
        // Intentar obtener IP real detrás de proxies/load balancers
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Si hay múltiples IPs (proxies), tomar la primera
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip != null ? ip : "UNKNOWN";
    }
    
    /**
     * Obtiene el email del usuario autenticado actualmente.
     * 
     * @return El email del usuario, o "SYSTEM" si no hay autenticación
     */
    public static String obtenerUsuarioEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "SYSTEM";
        }
        
        // El principal puede ser el username/email directamente
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            return (String) principal;
        }
        
        // O puede ser un objeto UserDetails con getUsername()
        try {
            return principal.getClass().getMethod("getUsername").invoke(principal).toString();
        } catch (Exception e) {
            return auth.getName();
        }
    }
    
    /**
     * Obtiene el rol principal del usuario autenticado.
     * 
     * @return El rol del usuario (ej: "ROLE_CLIENTE", "ROLE_ADMIN"), o "SYSTEM"
     */
    public static String obtenerUsuarioRol() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().isEmpty()) {
            return "SYSTEM";
        }
        
        // Obtener el primer rol (rol principal)
        return auth.getAuthorities().iterator().next().getAuthority();
    }
    
    /**
     * Obtiene el HttpServletRequest actual del contexto de Spring.
     * 
     * @return El request actual, o null si no hay contexto HTTP
     */
    private static HttpServletRequest obtenerRequestActual() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            // No hay contexto de request (ejecución desde job/tarea programada/test)
            return null;
        }
    }
}
