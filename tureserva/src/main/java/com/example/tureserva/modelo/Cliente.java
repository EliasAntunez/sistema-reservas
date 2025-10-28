package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cliente")
@PrimaryKeyJoinColumn(name = "usuario_id", foreignKey = @ForeignKey(name = "fk_cliente_usuario"))
@DiscriminatorValue("CLIENTE")
@Getter @Setter
@NoArgsConstructor
public class Cliente extends Usuario {
    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "proveedor_oauth2", length = 50)
    private String proveedorOAuth2; // "google", null para registro tradicional

    @Column(name = "id_oauth2")
    private String idOAuth2; // ID único del proveedor OAuth2

    @Column(name = "requiere_completar_datos")
    private Boolean requiereCompletarDatos = false; // true si necesita completar DNI después de OAuth2
}
