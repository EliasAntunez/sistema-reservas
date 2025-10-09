package com.example.tureserva.servicio;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.modelo.Cliente;

@Service
public class ServicioCliente {

    private final RepositorioCliente repositorioCliente;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection
    public ServicioCliente(RepositorioCliente repositorioCliente, PasswordEncoder passwordEncoder) {
        this.repositorioCliente = repositorioCliente;
        this.passwordEncoder = passwordEncoder;
    }

    // obtener todos los clientes
    public List<Cliente> obtenerTodosLosClientes() {
        return repositorioCliente.findAll();
    }

    // obtener clientes activos
    public List<Cliente> obtenerClientesActivos() {
        return repositorioCliente.findByActivoTrue();
    }

    // obtener cliente por id
    public Cliente obtenerClientePorId(Long id) {
        return repositorioCliente.findById(id).orElse(null);
    }

    // obtener cliente por email (solo activos para login)
    public Cliente obtenerClientePorEmail(String email) {
        return repositorioCliente.findByEmail(email).orElse(null);
    }
    
    // obtener cliente por email (incluye inactivos - para uso interno)
    public Cliente obtenerClientePorEmailCompleto(String email) {
        return repositorioCliente.findByEmail(email).orElse(null);
    }

    // guardar cliente
    public Cliente guardarCliente(Cliente cliente) {
        // normalizar datos antes de guardar
        if (cliente.getNombre() != null) {
            cliente.setNombre(cliente.getNombre().trim().toUpperCase());
        }
        if (cliente.getApellido() != null) {
            cliente.setApellido(cliente.getApellido().trim().toUpperCase());
        }
        
        // encriptar la contraseña antes de guardar
        if (cliente.getContrasena() != null && !cliente.getContrasena().isEmpty()) {
            String contrasenaEncriptada = passwordEncoder.encode(cliente.getContrasena());
            cliente.setContrasena(contrasenaEncriptada);
        }
        
        // configurar campos por defecto si no están establecidos
        if (cliente.getFechaRegistro() == null) {
            cliente.setFechaRegistro(LocalDateTime.now());
        }
        
        // marcar como activo por defecto
        cliente.setActivo(true);
        
        return repositorioCliente.save(cliente);
    }

    // actualizar cliente
    public Cliente actualizarCliente(Cliente cliente) {
        Cliente clienteExistente = repositorioCliente.findById(cliente.getId()).orElse(null);
        if (clienteExistente != null) {
            // normalizar datos antes de actualizar
            if (cliente.getNombre() != null) {
                clienteExistente.setNombre(cliente.getNombre().trim().toUpperCase());
            }
            if (cliente.getApellido() != null) {
                clienteExistente.setApellido(cliente.getApellido().trim().toUpperCase());
            }
            
            clienteExistente.setEmail(cliente.getEmail());
            clienteExistente.setTelefono(cliente.getTelefono());
            
            // Si la contraseña ha cambiado, encriptarla antes de actualizar
            if (cliente.getContrasena() != null && !cliente.getContrasena().isEmpty() &&
                !cliente.getContrasena().equals(clienteExistente.getContrasena())) {
                String contrasenaEncriptada = passwordEncoder.encode(cliente.getContrasena());
                clienteExistente.setContrasena(contrasenaEncriptada);
            }
            
            clienteExistente.setActivo(cliente.isActivo());
            return repositorioCliente.save(clienteExistente);
        }
        return null;
    }

    // eliminar cliente (físicamente para desarrollo, lógicamente para producción)
    @Transactional
    public boolean eliminarCliente(Long id) {
        try {
            Cliente cliente = repositorioCliente.findById(id).orElse(null);
            if (cliente != null) {
                // Para desarrollo: eliminación física
                repositorioCliente.delete(cliente);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // verificar si existe email
    public boolean verificarEmail(String email) {
        return repositorioCliente.existsByEmail(email);
    }

    // cambiar contraseña del cliente
    @Transactional
    public boolean cambiarContrasena(Long clienteId, String contrasenaActual, String nuevaContrasena) {
        try {
            Cliente cliente = repositorioCliente.findById(clienteId).orElse(null);
            if (cliente == null) {
                return false;
            }

            // Verificar que la contraseña actual sea correcta
            if (!passwordEncoder.matches(contrasenaActual, cliente.getContrasena())) {
                return false;
            }

            // Encriptar la nueva contraseña y guardar
            String nuevaContrasenaEncriptada = passwordEncoder.encode(nuevaContrasena);
            cliente.setContrasena(nuevaContrasenaEncriptada);
            repositorioCliente.save(cliente);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}