package com.example.tureserva.servicio;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import com.example.tureserva.modelo.AdministradorComplejo;

import com.example.tureserva.modelo.Cliente;

@Service
public class ServicioUsuarioUnificado {
    
    private final ServicioCliente servicioCliente;
    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    
    public ServicioUsuarioUnificado(ServicioCliente servicioCliente, ServicioAdministradorComplejo servicioAdministradorComplejo) {
        this.servicioCliente = servicioCliente;
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
    }
    
    // Clase interna para almacenar información del usuario de forma eficiente
    private static class InfoUsuario {
        final String email;
        final boolean esOAuth2;
        final Cliente cliente;
        final AdministradorComplejo adminComplejo;
        
        InfoUsuario(String email, boolean esOAuth2, Cliente cliente, AdministradorComplejo adminComplejo) {
            this.email = email;
            this.esOAuth2 = esOAuth2;
            this.cliente = cliente;
            this.adminComplejo = adminComplejo;
        }
    }
    
    /**
     * Método principal que obtiene toda la información del usuario de una vez
     * para evitar múltiples consultas a la base de datos
     */
    private InfoUsuario obtenerInfoUsuario(Authentication authentication) {
        boolean esOAuth2 = authentication instanceof OAuth2AuthenticationToken;
        String email = esOAuth2 
            ? ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttribute("email")
            : authentication.getName();
            
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        AdministradorComplejo adminComplejo = null;
        if (cliente == null) {
            // Intentar cargar como AdminComplejo si no es Cliente
            adminComplejo = servicioAdministradorComplejo.obtenerAdministradorPorEmail(email);
        }
        
        return new InfoUsuario(email, esOAuth2, cliente, adminComplejo);
    }
    
    /**
     * Obtiene el email del usuario
     */
    public String obtenerEmail(Authentication authentication) {
        return obtenerInfoUsuario(authentication).email;
    }
    
    /**
     * Obtiene el nombre para mostrar del usuario
     */
    public String obtenerNombreParaMostrar(Authentication authentication) {
        InfoUsuario info = obtenerInfoUsuario(authentication);
        
        // Si tiene cliente con nombre completo, usarlo
        if (info.cliente != null && info.cliente.getNombre() != null && info.cliente.getApellido() != null) {
            return info.cliente.getNombre() + " " + info.cliente.getApellido();
        }
        
        // Si es OAuth2 sin cliente, usar datos de Google
        if (info.esOAuth2) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            String nombre = oauth2Token.getPrincipal().getAttribute("given_name");
            String apellido = oauth2Token.getPrincipal().getAttribute("family_name");
            String nombreCompleto = oauth2Token.getPrincipal().getAttribute("name");
            
            if (nombre != null && apellido != null) {
                return nombre + " " + apellido;
            } else if (nombreCompleto != null) {
                return nombreCompleto;
            }
        }
        if (info.adminComplejo != null && info.adminComplejo.getNombre() != null && info.adminComplejo.getApellido() != null) {
            return info.adminComplejo.getNombre() + " " + info.adminComplejo.getApellido();
        }
        return info.email; // Fallback final al email
    }
    
    /**
     * Verifica si el usuario es OAuth2
     */
    public boolean esUsuarioOAuth2(Authentication authentication) {
        return authentication instanceof OAuth2AuthenticationToken;
    }
    
    /**
     * Obtiene el Cliente del usuario autenticado
     */
    public Cliente obtenerCliente(Authentication authentication) {
        return obtenerInfoUsuario(authentication).cliente;
    }
    
    /**
     * Obtiene el rol efectivo del usuario
     */
    public String obtenerRolEfectivo(Authentication authentication) {
        InfoUsuario info = obtenerInfoUsuario(authentication);
        
        if (info.esOAuth2) {
            return (info.cliente != null && info.cliente.getDni() != null && !info.cliente.getDni().isEmpty()) 
                ? "ROLE_CLIENTE" 
                : "ROLE_OAUTH2_TEMPORAL";
        } else {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(auth -> auth.getAuthority())
                    .orElse("ROLE_CLIENTE");
        }
    }
    
    /**
     * Obtiene el rol formateado de manera amigable para mostrar al usuario
     */
    public String obtenerRolFormateado(Authentication authentication) {
        String rolEfectivo = obtenerRolEfectivo(authentication);
        
        switch (rolEfectivo) {
            case "ROLE_SUPER_ADMIN":
                return "SuperAdmin";
            case "ROLE_ADMIN_COMPLEJO":
                return "AdminComplejo";
            case "ROLE_CLIENTE":
                return "Cliente";
            case "ROLE_OAUTH2_TEMPORAL":
                return "Completar registro";
            default:
                // Fallback: remove ROLE_ prefix and capitalize
                return rolEfectivo.startsWith("ROLE_") 
                    ? formatearRol(rolEfectivo.substring(5))
                    : formatearRol(rolEfectivo);
        }
    }
    
    /**
     * Método auxiliar para formatear roles personalizados
     */
    private String formatearRol(String rol) {
        if (rol == null || rol.isEmpty()) {
            return "Usuario";
        }
        
        // Convierte SUPER_ADMIN -> SuperAdmin, ADMIN_COMPLEJO -> AdminComplejo
        StringBuilder resultado = new StringBuilder();
        String[] partes = rol.toLowerCase().split("_");
        
        for (String parte : partes) {
            if (!parte.isEmpty()) {
                resultado.append(Character.toUpperCase(parte.charAt(0)))
                        .append(parte.substring(1));
            }
        }
        
        return resultado.toString();
    }
    
    /**
     * Verifica si el usuario OAuth2 necesita completar datos
     */
    public boolean necesitaCompletarDatos(Authentication authentication) {
        if (!esUsuarioOAuth2(authentication)) {
            return false;
        }
        
        InfoUsuario info = obtenerInfoUsuario(authentication);
        return info.cliente == null || info.cliente.getDni() == null || info.cliente.getDni().isEmpty();
    }
    
    /**
     * Crea un cliente temporal para mostrar en el formulario de completar datos
     */
    public Cliente crearClienteTemporal(Authentication authentication) {
        if (!esUsuarioOAuth2(authentication)) {
            return null;
        }
        
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        String email = oauth2Token.getPrincipal().getAttribute("email");
        String nombre = oauth2Token.getPrincipal().getAttribute("given_name");
        String apellido = oauth2Token.getPrincipal().getAttribute("family_name");
        String nombreCompleto = oauth2Token.getPrincipal().getAttribute("name");
        
        // Si no tenemos nombre/apellido separados, usar el nombre completo
        if (nombre == null && nombreCompleto != null) {
            String[] partesNombre = nombreCompleto.split(" ", 2);
            nombre = partesNombre[0];
            apellido = partesNombre.length > 1 ? partesNombre[1] : "";
        }
        
        Cliente clienteTemp = new Cliente();
        clienteTemp.setEmail(email);
        clienteTemp.setNombre(nombre != null ? nombre.trim().toUpperCase() : "USUARIO");
        clienteTemp.setApellido(apellido != null ? apellido.trim().toUpperCase() : "GOOGLE");
        
        return clienteTemp;
    }
    
    /**
     * Procesa y crea un cliente OAuth2 completo con DNI
     */
    public Cliente crearClienteOAuth2(Authentication authentication, String dni, String telefono) 
            throws IllegalArgumentException {
        
        if (!esUsuarioOAuth2(authentication)) {
            throw new IllegalArgumentException("Solo válido para usuarios OAuth2");
        }
        
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        String email = oauth2Token.getPrincipal().getAttribute("email");
        String nombre = oauth2Token.getPrincipal().getAttribute("given_name");
        String apellido = oauth2Token.getPrincipal().getAttribute("family_name");
        String idOAuth2 = oauth2Token.getPrincipal().getAttribute("sub");
        String nombreCompleto = oauth2Token.getPrincipal().getAttribute("name");
        
        // Si no tenemos nombre/apellido separados, usar el nombre completo
        if (nombre == null && nombreCompleto != null) {
            String[] partesNombre = nombreCompleto.split(" ", 2);
            nombre = partesNombre[0];
            apellido = partesNombre.length > 1 ? partesNombre[1] : "";
        }
        
        Cliente nuevoCliente = new Cliente();
        nuevoCliente.setEmail(email);
        nuevoCliente.setNombre(nombre != null ? nombre.trim().toUpperCase() : "USUARIO");
        nuevoCliente.setApellido(apellido != null ? apellido.trim().toUpperCase() : "GOOGLE");
        nuevoCliente.setDni(dni.trim().toUpperCase());
        nuevoCliente.setProveedorOAuth2("google");
        nuevoCliente.setIdOAuth2(idOAuth2);
        nuevoCliente.setActivo(true);
        nuevoCliente.setContrasena("OAUTH2_" + System.currentTimeMillis());
        nuevoCliente.setRequiereCompletarDatos(false);
        
        if (telefono != null && !telefono.trim().isEmpty()) {
            nuevoCliente.setTelefono(telefono.trim());
        }
        
        return nuevoCliente;
    }
}