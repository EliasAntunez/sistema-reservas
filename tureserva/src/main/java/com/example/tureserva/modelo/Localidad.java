package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "localidad",
    uniqueConstraints = {@UniqueConstraint(name = "uk_localidad_nombre_provincia", columnNames = {"nombre", "provincia_id"})}
)
@Getter @Setter
public class Localidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "provincia_id", foreignKey = @ForeignKey(name = "fk_localidad_provincia"))
    private Provincia provincia;
}
