package com.example.tureserva.modelo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = "espacio_reservable", 
    uniqueConstraints = { @UniqueConstraint(name = "uk_espacio_nombre_complejo", columnNames = {"nombre_espacio", "complejo_id"}) }
)
@Getter @Setter @NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_espacio", discriminatorType = DiscriminatorType.STRING, length = 20)

public abstract class EspacioReservable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_espacio_reservable")
    private Long id;

    @NotBlank(message = "El nombre del espacio no puede estar vacío")
    @Size(max = 100, message = "El nombre del espacio no puede exceder los 100 caracteres")
    @Column(name = "nombre_espacio", nullable = false, length = 100)
    private String nombre;

    @NotNull(message = "El campo 'capacidad' no puede ser nulo")
    @Column(name = "capacidad_espacio", length = 500)
    private int capacidad;

    @Min(value = 0, message = "El precio por hora no puede ser negativo")
    @NotNull(message = "El campo 'precio por hora' no puede ser nulo")
    @Column(name = "precio_por_hora")
    private Double precioPorHora;

    @Column(name = "activo")
    private Boolean activo = true;

    @ManyToOne
    @JoinColumn(name = "complejo_id", foreignKey = @ForeignKey(name = "fk_espacio_complejo"), nullable = false)
    private ComplejoDeportivo complejoDeportivo;

    @OneToMany(mappedBy = "espacioReservable", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<EspacioPoliticaSenia> espacioPoliticasSenia;

    @OneToMany(mappedBy = "espacioReservable", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<EspacioPoliticaCancelacion> espacioPoliticasCancelacion;
}
