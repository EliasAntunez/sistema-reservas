package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Entity
@Table(name = "pais",
     uniqueConstraints = {@UniqueConstraint(name = "uk_pais_nombre", columnNames = {"nombre"})}
)
@Getter @Setter
public class Pais {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nombre;

    @OneToMany(mappedBy = "pais", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Provincia> provincias;
}
