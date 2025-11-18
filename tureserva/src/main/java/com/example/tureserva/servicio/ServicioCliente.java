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
    
    // Constructor injection
    public ServicioCliente(RepositorioCliente repositorioCliente,
                           RepositorioReserva repositorioReserva,
                           PasswordEncoder passwordEncoder) {
        this.repositorioCliente = repositorioCliente;
        this.repositorioReserva = repositorioReserva;
        this.passwordEncoder = passwordEncoder;
    }

    // --- Métodos auxiliares de normalización (ubicados después del constructor) ---
    private String normalizarNombreApellido(String valor) {
        if (valor == null || valor.isEmpty()) return valor;
        String[] palabras = valor.trim().toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String palabra : palabras) {
            if (palabra.length() > 0) {
                sb.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1));
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private String normalizarDni(String dni) {
        if (dni == null) return null;
        String limpio = dni.replaceAll("[ .-]", "");
        return limpio.toUpperCase();
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
            cliente.setNombre(normalizarNombreApellido(cliente.getNombre()));
        }
        if (cliente.getApellido() != null) {
            cliente.setApellido(normalizarNombreApellido(cliente.getApellido()));
        }
        if (cliente.getDni() != null) {
            cliente.setDni(normalizarDni(cliente.getDni()));
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
        
        String codigo;
        int intentos = 0;
        int maxIntentos = 10; // Límite de seguridad para evitar un bucle infinito

        // --- Bucle de reintento para generar código único ---
        do {
            if (intentos > 0) {
                logger.warn("Colisión de código de Cliente. Reintentando... (Intento {})", intentos);
            }
            
            // 1. Genera un código aleatorio (6 caracteres)
            codigo = "CLI-" + UUID.randomUUID().toString()
                                    .substring(0, 6)
                                    .toUpperCase();
            
            intentos++;

            // 2. Seguridad: Si falla 10 veces, es porque 6 caracteres son muy pocos
            // para tu volumen de reservas y deberías usar 7 o 8.
            if (intentos > maxIntentos) {
                throw new RuntimeException("No se pudo generar un código de reserva único después de " + maxIntentos + " intentos.");
            }

        } while (repositorioReserva.existsByCodigoReserva(codigo)); // 3. Repite si el código ya existe
        // --- Fin del bucle ---

        // 4. Tenemos un código único, lo asignamos
        cliente.setCodigoUsuario(codigo);
        
        return repositorioCliente.save(cliente);
    }

    // actualizar cliente
    public Cliente actualizarCliente(Cliente cliente) {
        Cliente clienteExistente = repositorioCliente.findById(cliente.getId()).orElse(null);
        if (clienteExistente != null) {
            // normalizar datos antes de actualizar
            if (cliente.getNombre() != null) {
                clienteExistente.setNombre(normalizarNombreApellido(cliente.getNombre()));
            }
            if (cliente.getApellido() != null) {
                clienteExistente.setApellido(normalizarNombreApellido(cliente.getApellido()));
            }
            if (cliente.getDni() != null) {
                clienteExistente.setDni(normalizarDni(cliente.getDni()));
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