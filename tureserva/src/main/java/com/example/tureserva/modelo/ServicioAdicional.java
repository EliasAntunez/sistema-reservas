package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.example.tureserva.modelo.enums.AplicableA;
import com.example.tureserva.modelo.enums.TipoDeCobro;

import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "servicio_adicional", uniqueConstraints = {
    @UniqueConstraint(name = "uk_servicio_adicional_nombre_complejo", columnNames = {"nombre", "complejo_deportivo_id"})
})
@Getter @Setter @NoArgsConstructor
@ToString
public class ServicioAdicional implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "complejo_deportivo_id", nullable = false)
    private ComplejoDeportivo complejoDeportivo;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @Digits(integer = 8, fraction = 2)
    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_de_cobro", nullable = false, length = 32)
    private TipoDeCobro tipoDeCobro;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "aplicable_a", nullable = false, length = 32)
    private AplicableA aplicableA;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;

    // Máxima cantidad permitida para este servicio (null = sin límite administrable)
    @Min(1)
    @Column(name = "maximo_cantidad")
    private Integer maximoCantidad;

    // Relación inversa: un servicio puede aparecer en muchos detalles de reserva
    @ToString.Exclude
    @OneToMany(mappedBy = "servicioAdicional", fetch = FetchType.LAZY)
    private List<DetalleServicioAdicional> detalles = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (activo == null) activo = Boolean.TRUE;
        if (precio != null) {
            precio = precio.setScale(2, RoundingMode.HALF_UP);
        }
    }
}