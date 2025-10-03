package com.example.tureserva.modelo;

// JPA
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

// lombok
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clientes")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter @Setter
@NoArgsConstructor
public class Cliente extends Usuario {
    
    @Column(name = "telefono", length = 20)
    private String telefono;
}
