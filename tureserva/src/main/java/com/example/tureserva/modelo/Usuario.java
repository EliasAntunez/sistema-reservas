
package com.example.tureserva.modelo;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario",
     uniqueConstraints = {@UniqueConstraint(name = "uk_usuario_email", columnNames = {"email"}),
                           @UniqueConstraint(name = "uk_usuario_dni", columnNames = {"dni"})}
)
@Getter @Setter
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_usuario", nullable = true, unique = true, length = 20)
    private String codigoUsuario;

     @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

     @Column(name = "apellido", nullable = false, length = 50)
    private String apellido;

     @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "dni", nullable = true, length = 20)
    private String dni;

    /**
     * Contraseña del usuario (encriptada).
     * @JsonIgnore evita que se serialice en auditoría o respuestas API.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "contrasena", nullable = true)
    private String contrasena;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @PrePersist
    protected void onCreate() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
