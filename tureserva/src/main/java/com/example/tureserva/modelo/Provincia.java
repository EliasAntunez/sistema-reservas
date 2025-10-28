package com.example.tureserva.modelo;

import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "provincia",
     uniqueConstraints = {@UniqueConstraint(name = "uk_provincia_nombre_pais", columnNames = {"nombre", "pais_id"})}
)
@Getter @Setter
public class Provincia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "pais_id", foreignKey = @ForeignKey(name = "fk_provincia_pais"))
    private Pais pais;

    @OneToMany(mappedBy = "provincia", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Localidad> localidades;
}
