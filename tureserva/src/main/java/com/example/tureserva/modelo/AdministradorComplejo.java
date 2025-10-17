package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Entity
@Table(name = "administrador_complejo")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter @Setter
@NoArgsConstructor
public class AdministradorComplejo extends Usuario {
    
    @OneToMany(mappedBy = "administradorComplejo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ComplejoDeportivo> complejosDeportivos;
    
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