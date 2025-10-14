package com.example.tureserva.modelo;

import jakarta.persistence.*;

@Entity
@Table(name = "administrador_complejo")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class AdministradorComplejo extends Usuario {
    
    // Por ahora no necesita campos adicionales
    // La funcionalidad viene de la herencia de Usuario
    
    /**
     * Constructor vacío requerido por JPA
     */
    public AdministradorComplejo() {
        super();
    }
    
    /**
     * Constructor con parámetros
     */
    public AdministradorComplejo(String nombre, String apellido, String email, String dni, String contrasena) {
        setNombre(nombre);
        setApellido(apellido);
        setEmail(email);
        setDni(dni);
        setContrasena(contrasena);
    }
}