package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Entity
@Table(name = "administrador_complejo")
@PrimaryKeyJoinColumn(name = "usuario_id", foreignKey = @ForeignKey(name = "fk_admin_complejo_usuario"))
@DiscriminatorValue("ADMIN_COMPLEJO")
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

    /**
     * Token de acceso de Mercado Pago (por complejo/admin) para flujos
     * multi-tenant. Guardar en entorno de pruebas o vault en producción.
     * @JsonIgnore evita que se exponga en auditoría o respuestas API.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "mp_access_token", length = 1024)
    private String mpAccessToken;

    /**
     * Clave pública de Mercado Pago.
     * Aunque es pública, la ocultamos en auditoría por consistencia.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "mp_public_key", length = 512)
    private String mpPublicKey;
}