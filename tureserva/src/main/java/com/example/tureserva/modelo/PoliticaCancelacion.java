package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Entity
@Table(name = "politica_cancelacion",
    uniqueConstraints = { @UniqueConstraint(name = "uk_politica_cancelacion_nombre_complejo", columnNames = {"nombre_politica_cancelacion", "id_complejo"}) }
)
@Getter @Setter
public class PoliticaCancelacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_politica_cancelacion")
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    @Column(name = "nombre_politica_cancelacion", nullable = false, length = 100)
    private String nombre;

    @NotNull(message = "Las horas de anticipación son obligatorias")
    @Min(value = 0, message = "Las horas de anticipación deben ser 0 o más")
    @Column(name = "horas_anticipacion_minima", nullable = false)
    private Integer horasAnticipacionMinima;

    @NotNull(message = "El porcentaje de devolución es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El porcentaje debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", inclusive = true, message = "El porcentaje no puede superar 100")
    @Column(name = "porcentaje_devolucion", nullable = false)
    private Double porcentajeDevolucion;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @NotNull(message = "La fecha de fin es obligatoria")
    @Column(name = "fecha_fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne
    @JoinColumn(name = "id_complejo", nullable = false, foreignKey = @ForeignKey(name = "fk_politica_cancelacion_complejo"))
    @NotNull(message = "El complejo deportivo es obligatorio")
    private ComplejoDeportivo complejoDeportivo;
}
