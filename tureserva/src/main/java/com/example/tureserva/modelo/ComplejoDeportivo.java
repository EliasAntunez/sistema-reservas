package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Entity
@Table(name = "complejo_deportivo",
       uniqueConstraints = {@UniqueConstraint(name = "uk_complejo_nombre_direccion", columnNames = {"nombre_complejo", "direccion_complejo"})}
)
@Getter @Setter
public class ComplejoDeportivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_complejo;

    @Column(nullable = false)
    private String nombre_complejo;



    @ManyToOne
    @JoinColumn(name = "localidad_id", foreignKey = @ForeignKey(name = "fk_complejo_localidad"))
    private Localidad localidad;

    @Column(nullable = false)
    private String direccion_complejo;

    @ManyToOne
    @JoinColumn(name = "administrador_id", foreignKey = @ForeignKey(name = "fk_complejo_administrador"))
    private AdministradorComplejo administradorComplejo;

    @Column(nullable = false)
    private boolean activo = true;

    // Relación uno a muchos con HorarioComplejo
    @OneToMany(mappedBy = "complejoDeportivo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HorarioComplejo> horariosComplejo;
}
