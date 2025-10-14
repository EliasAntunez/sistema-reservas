package com.example.tureserva.servicio;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tureserva.repositorio.RepositorioAdministradorComplejo;
import com.example.tureserva.modelo.AdministradorComplejo;

@Service
public class ServicioAdministradorComplejo {

    private final RepositorioAdministradorComplejo repositorioAdministradorComplejo;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection
    public ServicioAdministradorComplejo(RepositorioAdministradorComplejo repositorioAdministradorComplejo, 
                                        PasswordEncoder passwordEncoder) {
        this.repositorioAdministradorComplejo = repositorioAdministradorComplejo;
        this.passwordEncoder = passwordEncoder;
    }

    // Obtener todos los AdministradoresComplejo
    public List<AdministradorComplejo> obtenerTodosLosAdministradores() {
        return repositorioAdministradorComplejo.findAll();
    }

    // Obtener AdministradoresComplejo activos
    public List<AdministradorComplejo> obtenerAdministradoresActivos() {
        return repositorioAdministradorComplejo.findByActivoTrue();
    }

    // Obtener AdministradorComplejo por id
    public AdministradorComplejo obtenerAdministradorPorId(Long id) {
        return repositorioAdministradorComplejo.findById(id).orElse(null);
    }

    // Obtener AdministradorComplejo por email
    public AdministradorComplejo obtenerAdministradorPorEmail(String email) {
        return repositorioAdministradorComplejo.findByEmail(email).orElse(null);
    }
    
    // Obtener AdministradorComplejo activo por email (para login)
    public AdministradorComplejo obtenerAdministradorActivoPorEmail(String email) {
        return repositorioAdministradorComplejo.findByEmailAndActivoTrue(email).orElse(null);
    }

    // Obtener AdministradorComplejo por DNI
    public AdministradorComplejo obtenerAdministradorPorDni(String dni) {
        return repositorioAdministradorComplejo.findByDni(dni).orElse(null);
    }

    // Guardar AdministradorComplejo
    @Transactional
    public AdministradorComplejo guardarAdministrador(AdministradorComplejo admin) {
        // Normalizar datos antes de guardar
        if (admin.getNombre() != null) {
            admin.setNombre(admin.getNombre().trim().toUpperCase());
        }
        if (admin.getApellido() != null) {
            admin.setApellido(admin.getApellido().trim().toUpperCase());
        }
        if (admin.getEmail() != null) {
            admin.setEmail(admin.getEmail().trim().toLowerCase());
        }
        if (admin.getDni() != null) {
            admin.setDni(admin.getDni().trim().toUpperCase());
        }
        
        // Encriptar la contraseña antes de guardar
        if (admin.getContrasena() != null && !admin.getContrasena().isEmpty()) {
            String contrasenaEncriptada = passwordEncoder.encode(admin.getContrasena());
            admin.setContrasena(contrasenaEncriptada);
        }
        
        // Configurar campos por defecto si no están establecidos
        if (admin.getFechaRegistro() == null) {
            admin.setFechaRegistro(LocalDateTime.now());
        }
        
        // Marcar como activo por defecto
        admin.setActivo(true);
        
        return repositorioAdministradorComplejo.save(admin);
    }

    // Actualizar AdministradorComplejo
    @Transactional
    public AdministradorComplejo actualizarAdministrador(AdministradorComplejo admin) {
        AdministradorComplejo existente = repositorioAdministradorComplejo.findById(admin.getId()).orElse(null);
        if (existente != null) {
            // Normalizar datos antes de actualizar
            if (admin.getNombre() != null) {
                existente.setNombre(admin.getNombre().trim().toUpperCase());
            }
            if (admin.getApellido() != null) {
                existente.setApellido(admin.getApellido().trim().toUpperCase());
            }
            if (admin.getEmail() != null) {
                existente.setEmail(admin.getEmail().trim().toLowerCase());
            }
            if (admin.getDni() != null) {
                existente.setDni(admin.getDni().trim().toUpperCase());
            }
            
            // Si la contraseña ha cambiado, encriptarla antes de actualizar
            if (admin.getContrasena() != null && !admin.getContrasena().isEmpty() &&
                !admin.getContrasena().equals(existente.getContrasena())) {
                String contrasenaEncriptada = passwordEncoder.encode(admin.getContrasena());
                existente.setContrasena(contrasenaEncriptada);
            }
            
            existente.setActivo(admin.isActivo());
            return repositorioAdministradorComplejo.save(existente);
        }
        return null;
    }

    // Cambiar contraseña del AdministradorComplejo validando la actual
    @Transactional
    public boolean cambiarContrasena(Long adminId, String contrasenaActual, String nuevaContrasena) {
        try {
            AdministradorComplejo admin = repositorioAdministradorComplejo.findById(adminId).orElse(null);
            if (admin == null) {
                return false;
            }

            // Verificar que la contraseña actual sea correcta
            if (!passwordEncoder.matches(contrasenaActual, admin.getContrasena())) {
                return false;
            }

            // Encriptar la nueva contraseña y guardar
            String nuevaContrasenaEncriptada = passwordEncoder.encode(nuevaContrasena);
            admin.setContrasena(nuevaContrasenaEncriptada);
            repositorioAdministradorComplejo.save(admin);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Eliminar AdministradorComplejo (eliminación lógica)  
    @Transactional
    public boolean eliminarAdministrador(Long id) {
        try {
            AdministradorComplejo admin = repositorioAdministradorComplejo.findById(id).orElse(null);
            if (admin != null) {
                // Eliminación lógica - marcar como inactivo
                admin.setActivo(false);
                repositorioAdministradorComplejo.save(admin);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Verificar si existe email
    public boolean verificarEmail(String email) {
        return repositorioAdministradorComplejo.existsByEmail(email);
    }

    // Verificar si existe DNI
    public boolean verificarDni(String dni) {
        return repositorioAdministradorComplejo.existsByDni(dni);
    }

    // Contar AdministradoresComplejo activos
    public long contarAdministradoresActivos() {
        return repositorioAdministradorComplejo.countByActivoTrue();
    }
}
