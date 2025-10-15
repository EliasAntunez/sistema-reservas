package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "administrador_complejo")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter @Setter
@NoArgsConstructor
public class AdministradorComplejo extends Usuario {
    
    // Por ahora no necesita campos adicionales
    // La funcionalidad viene de la herencia de Usuario
    
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