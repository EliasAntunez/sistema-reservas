package com.example.tureserva.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "deporte", 
       uniqueConstraints = {@UniqueConstraint(name = "uk_deporte_nombre", columnNames = {"nombre_deporte"})}
)
@Getter @Setter @NoArgsConstructor
public class Deporte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_deporte")
    private Long id;

    @Column(name = "nombre_deporte", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "activo")
    private Boolean activo = true;
}
