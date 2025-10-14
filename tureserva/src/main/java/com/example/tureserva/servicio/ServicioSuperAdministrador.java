package com.example.tureserva.servicio;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tureserva.repositorio.RepositorioSuperAdministrador;
import com.example.tureserva.modelo.SuperAdministrador;

@Service
public class ServicioSuperAdministrador {

    private final RepositorioSuperAdministrador repositorioSuperAdministrador;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection
    public ServicioSuperAdministrador(RepositorioSuperAdministrador repositorioSuperAdministrador, 
                                     PasswordEncoder passwordEncoder) {
        this.repositorioSuperAdministrador = repositorioSuperAdministrador;
        this.passwordEncoder = passwordEncoder;
    }

    // Obtener todos los SuperAdministradores
    public List<SuperAdministrador> obtenerTodosLosSuperAdministradores() {
        return repositorioSuperAdministrador.findAll();
    }

    // Obtener SuperAdministradores activos
    public List<SuperAdministrador> obtenerSuperAdministradoresActivos() {
        return repositorioSuperAdministrador.findByActivoTrue();
    }

    // Obtener SuperAdministrador por id
    public SuperAdministrador obtenerSuperAdministradorPorId(Long id) {
        return repositorioSuperAdministrador.findById(id).orElse(null);
    }

    // Obtener SuperAdministrador por email
    public SuperAdministrador obtenerSuperAdministradorPorEmail(String email) {
        return repositorioSuperAdministrador.findByEmail(email).orElse(null);
    }
    
    // Obtener SuperAdministrador activo por email (para login)
    public SuperAdministrador obtenerSuperAdministradorActivoPorEmail(String email) {
        return repositorioSuperAdministrador.findByEmailAndActivoTrue(email).orElse(null);
    }

    // Obtener SuperAdministrador por DNI
    public SuperAdministrador obtenerSuperAdministradorPorDni(String dni) {
        return repositorioSuperAdministrador.findByDni(dni).orElse(null);
    }

    // Guardar SuperAdministrador
    @Transactional
    public SuperAdministrador guardarSuperAdministrador(SuperAdministrador superAdmin) {
        // Normalizar datos antes de guardar
        if (superAdmin.getNombre() != null) {
            superAdmin.setNombre(superAdmin.getNombre().trim().toUpperCase());
        }
        if (superAdmin.getApellido() != null) {
            superAdmin.setApellido(superAdmin.getApellido().trim().toUpperCase());
        }
        if (superAdmin.getEmail() != null) {
            superAdmin.setEmail(superAdmin.getEmail().trim().toLowerCase());
        }
        if (superAdmin.getDni() != null) {
            superAdmin.setDni(superAdmin.getDni().trim().toUpperCase());
        }
        
        // Encriptar la contraseña antes de guardar
        if (superAdmin.getContrasena() != null && !superAdmin.getContrasena().isEmpty()) {
            String contrasenaEncriptada = passwordEncoder.encode(superAdmin.getContrasena());
            superAdmin.setContrasena(contrasenaEncriptada);
        }
        
        // Configurar campos por defecto si no están establecidos
        if (superAdmin.getFechaRegistro() == null) {
            superAdmin.setFechaRegistro(LocalDateTime.now());
        }
        
        // Marcar como activo por defecto
        superAdmin.setActivo(true);
        
        return repositorioSuperAdministrador.save(superAdmin);
    }

    // Actualizar SuperAdministrador
    @Transactional
    public SuperAdministrador actualizarSuperAdministrador(SuperAdministrador superAdmin) {
        SuperAdministrador existente = repositorioSuperAdministrador.findById(superAdmin.getId()).orElse(null);
        if (existente != null) {
            // Normalizar datos antes de actualizar
            if (superAdmin.getNombre() != null) {
                existente.setNombre(superAdmin.getNombre().trim().toUpperCase());
            }
            if (superAdmin.getApellido() != null) {
                existente.setApellido(superAdmin.getApellido().trim().toUpperCase());
            }
            if (superAdmin.getEmail() != null) {
                existente.setEmail(superAdmin.getEmail().trim().toLowerCase());
            }
            if (superAdmin.getDni() != null) {
                existente.setDni(superAdmin.getDni().trim().toUpperCase());
            }
            
            // Si la contraseña ha cambiado, encriptarla antes de actualizar
            if (superAdmin.getContrasena() != null && !superAdmin.getContrasena().isEmpty() &&
                !superAdmin.getContrasena().equals(existente.getContrasena())) {
                String contrasenaEncriptada = passwordEncoder.encode(superAdmin.getContrasena());
                existente.setContrasena(contrasenaEncriptada);
            }
            
            existente.setActivo(superAdmin.isActivo());
            return repositorioSuperAdministrador.save(existente);
        }
        return null;
    }

    // Actualizar solo la contraseña
    @Transactional
    public boolean actualizarContrasena(Long id, String nuevaContrasena) {
        SuperAdministrador existente = repositorioSuperAdministrador.findById(id).orElse(null);
        if (existente != null && nuevaContrasena != null && !nuevaContrasena.isEmpty()) {
            String contrasenaEncriptada = passwordEncoder.encode(nuevaContrasena);
            existente.setContrasena(contrasenaEncriptada);
            repositorioSuperAdministrador.save(existente);
            return true;
        }
        return false;
    }

    // cambiar contraseña del SuperAdministrador validando la actual
    @Transactional
    public boolean cambiarContrasena(Long superAdminId, String contrasenaActual, String nuevaContrasena) {
        try {
            SuperAdministrador superAdmin = repositorioSuperAdministrador.findById(superAdminId).orElse(null);
            if (superAdmin == null) {
                return false;
            }

            // Verificar que la contraseña actual sea correcta
            if (!passwordEncoder.matches(contrasenaActual, superAdmin.getContrasena())) {
                return false;
            }

            // Encriptar la nueva contraseña y guardar
            String nuevaContrasenaEncriptada = passwordEncoder.encode(nuevaContrasena);
            superAdmin.setContrasena(nuevaContrasenaEncriptada);
            repositorioSuperAdministrador.save(superAdmin);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Eliminar SuperAdministrador (eliminación lógica)
    @Transactional
    public boolean eliminarSuperAdministrador(Long id) {
        try {
            SuperAdministrador superAdmin = repositorioSuperAdministrador.findById(id).orElse(null);
            if (superAdmin != null) {
                // Eliminación lógica - marcar como inactivo
                superAdmin.setActivo(false);
                repositorioSuperAdministrador.save(superAdmin);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Verificar si existe email
    public boolean verificarEmail(String email) {
        return repositorioSuperAdministrador.existsByEmail(email);
    }

    // Verificar si existe DNI
    public boolean verificarDni(String dni) {
        return repositorioSuperAdministrador.existsByDni(dni);
    }

    // Contar SuperAdministradores activos
    public long contarSuperAdministradoresActivos() {
        return repositorioSuperAdministrador.countByActivoTrue();
    }
}