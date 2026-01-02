package com.example.tureserva.servicio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.modelo.Cliente;

@Service
public class ServicioCliente {
    private static final Logger logger = LoggerFactory.getLogger(ServicioCliente.class);
    private final RepositorioCliente repositorioCliente;
    private final RepositorioReserva repositorioReserva;
    private final PasswordEncoder passwordEncoder;
    private final ServicioNormalizacion servicioNormalizacion;
    private final ServicioGeneradorCodigos servicioGeneradorCodigos;
    
    // Constructor injection
    public ServicioCliente(RepositorioCliente repositorioCliente,
                           RepositorioReserva repositorioReserva,
                          PasswordEncoder passwordEncoder,
                          ServicioNormalizacion servicioNormalizacion,
                          ServicioGeneradorCodigos servicioGeneradorCodigos) {
        this.repositorioCliente = repositorioCliente;
        this.repositorioReserva = repositorioReserva;
        this.passwordEncoder = passwordEncoder;
        this.servicioNormalizacion = servicioNormalizacion;
        this.servicioGeneradorCodigos = servicioGeneradorCodigos;
    }
    
    // ELIMINADO: Métodos de normalización movidos a ServicioNormalizacion
    // - normalizarNombreApellido() → servicioNormalizacion.normalizarNombreApellido()
    // - normalizarDni() → servicioNormalizacion.normalizarDni()

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
        // Normalizar datos antes de guardar usando servicio centralizado
        if (cliente.getNombre() != null) {
            cliente.setNombre(servicioNormalizacion.normalizarNombreApellido(cliente.getNombre()));
        }
        if (cliente.getApellido() != null) {
            cliente.setApellido(servicioNormalizacion.normalizarNombreApellido(cliente.getApellido()));
        }
        if (cliente.getDni() != null) {
            cliente.setDni(servicioNormalizacion.normalizarDni(cliente.getDni()));
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
        
        // Generar código único de cliente usando servicio centralizado
        String codigo = servicioGeneradorCodigos.generarCodigoCliente(
            codigoGenerado -> repositorioReserva.existsByCodigoReserva(codigoGenerado)
        );
        cliente.setCodigoUsuario(codigo);
        
        return repositorioCliente.save(cliente);
    }

    // actualizar cliente
    public Cliente actualizarCliente(Cliente cliente) {
        Cliente clienteExistente = repositorioCliente.findById(cliente.getId()).orElse(null);
        if (clienteExistente != null) {
            // Normalizar datos antes de actualizar usando servicio centralizado
            if (cliente.getNombre() != null) {
                clienteExistente.setNombre(servicioNormalizacion.normalizarNombreApellido(cliente.getNombre()));
            }
            if (cliente.getApellido() != null) {
                clienteExistente.setApellido(servicioNormalizacion.normalizarNombreApellido(cliente.getApellido()));
            }
            if (cliente.getDni() != null) {
                clienteExistente.setDni(servicioNormalizacion.normalizarDni(cliente.getDni()));
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
            
            // Actualizar campos OAuth2 específicos
            if (cliente.getProveedorOAuth2() != null) {
                clienteExistente.setProveedorOAuth2(cliente.getProveedorOAuth2());
            }
            if (cliente.getIdOAuth2() != null) {
                clienteExistente.setIdOAuth2(cliente.getIdOAuth2());
            }
            if (cliente.getRequiereCompletarDatos() != null) {
                clienteExistente.setRequiereCompletarDatos(cliente.getRequiereCompletarDatos());
            }
            
            return repositorioCliente.save(clienteExistente);
        }
        return null;
    }

    // eliminar cliente: baja lógica (marcar inactivo) verificando reservas activas
    @Transactional
    public boolean eliminarCliente(Long id) {
        Cliente cliente = repositorioCliente.findById(id).orElse(null);
        if (cliente == null) {
            return false;
        }

        // Contar reservas activas (PENDIENTE o CONFIRMADA)
        long pendientes = repositorioReserva.countByClienteAndEstado(cliente, EstadoReserva.PENDIENTE);
        long confirmadas = repositorioReserva.countByClienteAndEstado(cliente, EstadoReserva.CONFIRMADA);

        if (pendientes + confirmadas > 0) {
            throw new IllegalStateException("El cliente tiene reservas activas. Debe cancelar todas sus reservas antes de darse de baja.");
        }

        // Dar de baja lógicamente
        cliente.setActivo(false);
        repositorioCliente.save(cliente);
        return true;
    }

    // verificar si existe email
    public boolean verificarEmail(String email) {
        return repositorioCliente.existsByEmail(email);
    }

    // verificar si existe DNI
    public boolean verificarDni(String dni) {
        return repositorioCliente.existsByDni(dni);
    }

    // obtener cliente por DNI
    public Cliente obtenerClientePorDni(String dni) {
        return repositorioCliente.findByDni(dni).orElse(null);
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