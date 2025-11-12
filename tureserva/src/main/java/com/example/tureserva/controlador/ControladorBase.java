package com.example.tureserva.controlador;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Clase base para controladores que proporciona métodos utilitarios comunes.
 * Todos los controladores de la aplicación pueden extender esta clase.
 * 
 * @author TuReserva
 * @version 1.0
 */
@Slf4j
public abstract class ControladorBase {
    
    /**
     * Verifica si el usuario autenticado tiene permiso para gestionar un complejo deportivo.
     * 
     * <p>Para un proyecto académico, esta validación es básica y verifica que:
     * <ul>
     *   <li>El usuario esté autenticado</li>
     *   <li>El complejo tenga un administrador asignado</li>
     *   <li>El usuario sea el administrador del complejo</li>
     * </ul>
     * 
     * @param complejo El complejo deportivo a verificar
     * @return true si el usuario tiene permiso, false en caso contrario
     */
    protected boolean tieneAccesoAlComplejo(ComplejoDeportivo complejo) {
        if (complejo == null) {
            log.warn("Intento de verificar acceso a complejo nulo");
            return false;
        }
        
        // Obtener usuario autenticado
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            log.warn("Intento de acceso sin autenticación al complejo ID: {}", complejo.getId_complejo());
            return false;
        }
        
        // Verificar que el complejo tenga administrador
        AdministradorComplejo admin = complejo.getAdministradorComplejo();
        if (admin == null) {
            log.warn("El complejo ID: {} no tiene administrador asignado", complejo.getId_complejo());
            return false;
        }
        
        // Obtener el usuario actual (el principal puede ser String o UserDetails)
        Object principal = auth.getPrincipal();
        String emailUsuario = null;
        
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            emailUsuario = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            emailUsuario = (String) principal;
        }
        
        if (emailUsuario == null) {
            log.warn("No se pudo obtener el email del usuario autenticado");
            return false;
        }
        
        // Verificar que el usuario sea el administrador del complejo
        // AdministradorComplejo extiende de Usuario, así que comparamos directamente
        if (!emailUsuario.equals(admin.getEmail())) {
            log.warn("Usuario {} intentó acceder al complejo ID: {} sin permisos", 
                    emailUsuario, complejo.getId_complejo());
            return false;
        }
        
        log.debug("Acceso autorizado para usuario {} al complejo ID: {}", 
                emailUsuario, complejo.getId_complejo());
        return true;
    }
    
    /**
     * Obtiene el usuario autenticado actualmente.
     * 
     * @return El usuario autenticado, o null si no hay usuario autenticado
     */
    protected Usuario getUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        
        // En un proyecto real, aquí obtendrías el Usuario completo desde la base de datos
        // Para simplicidad académica, retornamos null si no está implementado
        // Implementar según tu lógica de autenticación
        return null;
    }
    
    /**
     * Verifica si hay un usuario autenticado.
     * 
     * @return true si hay un usuario autenticado, false en caso contrario
     */
    protected boolean estaAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }
}
