package com.example.tureserva.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.tureserva.modelo.SuperAdministrador;
import com.example.tureserva.servicio.ServicioSuperAdministrador;

/**
 * Clase para cargar datos iniciales en la aplicación
 * Se ejecuta automáticamente al iniciar Spring Boot
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private final ServicioSuperAdministrador servicioSuperAdministrador;

    public DatosIniciales(ServicioSuperAdministrador servicioSuperAdministrador) {
        this.servicioSuperAdministrador = servicioSuperAdministrador;
    }

    @Override
    public void run(String... args) throws Exception {
        crearSuperAdministradorInicial();
    }

    /**
     * Crea un SuperAdministrador inicial si no existe ninguno
     */
    private void crearSuperAdministradorInicial() {
        try {
            // Verificar si ya existe un SuperAdministrador
            long cantidadSuperAdmins = servicioSuperAdministrador.contarSuperAdministradoresActivos();
            
            if (cantidadSuperAdmins == 0) {
                System.out.println("🚀 No se encontraron SuperAdministradores. Creando SuperAdministrador inicial...");
                
                // Crear SuperAdministrador inicial
                SuperAdministrador superAdminInicial = new SuperAdministrador(
                    "ADMIN",                    // nombre
                    "SISTEMA",                  // apellido  
                    "admin@gmail.com",      // email
                    "12345678",                 // dni
                    "tio mono"                  // contraseña
                );
                
                // Guardar usando el servicio (que se encarga de encriptar la contraseña)
                SuperAdministrador guardado = servicioSuperAdministrador.guardarSuperAdministrador(superAdminInicial);
                
                if (guardado != null) {
                    System.out.println("✅ SuperAdministrador inicial creado exitosamente:");
                    System.out.println("   📧 Email: " + guardado.getEmail());
                    System.out.println("   🆔 DNI: " + guardado.getDni());
                    System.out.println("   🔑 Contraseña: tio mono");
                    System.out.println("   📅 Fecha: " + guardado.getFechaRegistro());
                    System.out.println("   ℹ️  Usa estas credenciales para iniciar sesión como SuperAdministrador");
                } else {
                    System.err.println("❌ Error al crear SuperAdministrador inicial");
                }
            } else {
                System.out.println("ℹ️  Ya existen " + cantidadSuperAdmins + " SuperAdministrador(es) activo(s). No se creará uno nuevo.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error al verificar/crear SuperAdministrador inicial: " + e.getMessage());
            e.printStackTrace();
        }
    }
}