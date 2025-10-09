package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "super_administradores")
@Getter @Setter
@NoArgsConstructor
public class SuperAdministrador extends Usuario {
    
    // Por ahora no necesita campos adicionales
    // La funcionalidad viene de la herencia de Usuario
    
    public SuperAdministrador(String nombre, String apellido, String email, String contrasena) {
        setNombre(nombre);
        setApellido(apellido);
        setEmail(email);
        setContrasena(contrasena);
    }
}