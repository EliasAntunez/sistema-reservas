package com.example.tureserva.servicio;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio centralizado para validar permisos de acceso a recursos.
 * Evita duplicación de código en controladores y proporciona validaciones consistentes.
 */
@Service
public class ServicioValidacionPermisos {

    private static final Logger logger = LoggerFactory.getLogger(ServicioValidacionPermisos.class);

    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;
    private final ServicioUsuarioUnificado servicioUsuarioUnificado;

    public ServicioValidacionPermisos(
            ServicioAdministradorComplejo servicioAdministradorComplejo,
            ServicioComplejoDeportivo servicioComplejoDeportivo,
            ServicioUsuarioUnificado servicioUsuarioUnificado) {
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
        this.servicioUsuarioUnificado = servicioUsuarioUnificado;
    }

    /**
     * Verifica si el usuario tiene rol de Super Admin.
     */
    public boolean esSuperAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Verifica si el usuario tiene rol de Admin de Complejo.
     */
    public boolean esAdminComplejo(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN_COMPLEJO".equals(a.getAuthority()));
    }

    /**
     * Verifica si el usuario tiene rol de Cliente.
     */
    public boolean esCliente(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLIENTE".equals(a.getAuthority()));
    }

    /**
     * Obtiene el AdministradorComplejo correspondiente al usuario autenticado.
     * 
     * @param authentication Autenticación del usuario
     * @return AdministradorComplejo o null si no existe o no tiene ese rol
     */
    public AdministradorComplejo obtenerAdministradorComplejo(Authentication authentication) {
        if (!esAdminComplejo(authentication)) {
            return null;
        }
        
        String email = servicioUsuarioUnificado.obtenerEmail(authentication);
        if (email == null) {
            logger.warn("No se pudo obtener email del usuario autenticado");
            return null;
        }
        
        return servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
    }

    /**
     * Obtiene la lista de complejos a los que el usuario tiene acceso según su rol.
     * IMPORTANTE: Carga los horarios master con JOIN FETCH para uso en reportes.
     * 
     * @param authentication Autenticación del usuario
     * @return Lista de complejos permitidos con horarios cargados
     */
    public List<ComplejoDeportivo> obtenerComplejosPermitidos(Authentication authentication) {
        if (esSuperAdmin(authentication)) {
            // Super Admin: todos los complejos activos CON HORARIOS
            return servicioComplejoDeportivo.obtenerComplejosActivosConHorarios();
        }
        
        if (esAdminComplejo(authentication)) {
            // Admin Complejo: solo sus complejos CON HORARIOS
            AdministradorComplejo admin = obtenerAdministradorComplejo(authentication);
            if (admin != null) {
                return servicioComplejoDeportivo.obtenerComplejosPorAdministradorConHorarios(admin.getId());
            }
        }
        
        // Otros roles: sin acceso a complejos
        return List.of();
    }

    /**
     * Valida si el usuario tiene permiso para acceder a un complejo específico.
     * 
     * @param authentication Autenticación del usuario
     * @param complejoId ID del complejo a verificar
     * @return true si tiene permiso, false si no
     */
    public boolean tienePermisoSobreComplejo(Authentication authentication, Long complejoId) {
        if (complejoId == null) {
            return true; // Si no se especifica complejo, permitir (se validará a nivel de negocio)
        }
        
        if (esSuperAdmin(authentication)) {
            return true; // Super Admin tiene acceso a todos
        }
        
        if (esAdminComplejo(authentication)) {
            List<ComplejoDeportivo> complejosPermitidos = obtenerComplejosPermitidos(authentication);
            return complejosPermitidos.stream()
                    .anyMatch(c -> c.getId_complejo().equals(complejoId));
        }
        
        return false;
    }

    /**
     * Valida permisos y lanza excepción si no tiene acceso.
     * Útil para validaciones en servicios.
     * 
     * @param authentication Autenticación del usuario
     * @param complejoId ID del complejo a verificar
     * @throws SecurityException si no tiene permisos
     */
    public void validarPermisoSobreComplejoOThrow(Authentication authentication, Long complejoId) {
        if (!tienePermisoSobreComplejo(authentication, complejoId)) {
            String email = authentication != null ? authentication.getName() : "anónimo";
            logger.warn("⚠️ SEGURIDAD: Usuario '{}' intentó acceder sin permiso al complejo ID: {}", 
                email, complejoId);
            throw new SecurityException("No tienes permisos para acceder a este complejo");
        }
    }

    /**
     * Resultado de validación con información adicional para logging.
     */
    public static class ResultadoValidacion {
        private final boolean permitido;
        private final String razon;
        private final String rolDetectado;
        
        public ResultadoValidacion(boolean permitido, String razon, String rolDetectado) {
            this.permitido = permitido;
            this.razon = razon;
            this.rolDetectado = rolDetectado;
        }
        
        public boolean esPermitido() { return permitido; }
        public String getRazon() { return razon; }
        public String getRolDetectado() { return rolDetectado; }
    }

    /**
     * Validación detallada con información para logging y debugging.
     * 
     * @param authentication Autenticación del usuario
     * @param complejoId ID del complejo a verificar
     * @return ResultadoValidacion con detalles de la validación
     */
    public ResultadoValidacion validarPermisoDetallado(Authentication authentication, Long complejoId) {
        if (authentication == null) {
            return new ResultadoValidacion(false, "Usuario no autenticado", "NINGUNO");
        }
        
        if (esSuperAdmin(authentication)) {
            return new ResultadoValidacion(true, "Super Admin tiene acceso total", "SUPER_ADMIN");
        }
        
        if (esAdminComplejo(authentication)) {
            if (complejoId == null) {
                return new ResultadoValidacion(true, "No se especificó complejo", "ADMIN_COMPLEJO");
            }
            
            boolean tienePermiso = tienePermisoSobreComplejo(authentication, complejoId);
            String razon = tienePermiso 
                ? "Admin tiene acceso al complejo " + complejoId
                : "Admin NO tiene acceso al complejo " + complejoId;
            
            return new ResultadoValidacion(tienePermiso, razon, "ADMIN_COMPLEJO");
        }
        
        return new ResultadoValidacion(false, "Usuario sin rol de administrador", "CLIENTE_U_OTRO");
    }

    /**
     * Registra intento de acceso no autorizado en los logs.
     * 
     * @param authentication Autenticación del usuario
     * @param recurso Descripción del recurso al que intentó acceder
     * @param complejoId ID del complejo (opcional)
     */
    public void registrarIntentoNoAutorizado(Authentication authentication, String recurso, Long complejoId) {
        String usuario = authentication != null ? authentication.getName() : "anónimo";
        String roles = authentication != null 
            ? authentication.getAuthorities().toString() 
            : "sin roles";
        
        if (complejoId != null) {
            logger.warn("🚨 INTENTO NO AUTORIZADO: Usuario '{}' (roles: {}) intentó acceder a {} del complejo ID: {}", 
                usuario, roles, recurso, complejoId);
        } else {
            logger.warn("🚨 INTENTO NO AUTORIZADO: Usuario '{}' (roles: {}) intentó acceder a {}", 
                usuario, roles, recurso);
        }
    }
}
