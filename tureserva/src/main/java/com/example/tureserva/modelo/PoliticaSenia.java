package com.example.tureserva.modelo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.ForeignKey;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.JoinColumn;

@Entity
@Table(name = "politica_senia",
    uniqueConstraints = { @UniqueConstraint(name = "uk_politica_senia_nombre_complejo", columnNames = {"nombre_politica_senia", "id_complejo"}) }
)
@Getter @Setter

public class PoliticaSenia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_politica_senia")
    private Long id;

    @NotNull(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Column(name = "nombre_politica_senia", nullable = false, length = 100)
    private String nombre;

    @Min(value = 0, message = "El porcentaje no puede ser negativo")
    @Max(value = 100, message = "El porcentaje no puede superar 100")
    @Column(name = "porcentaje_senia", nullable = true)
    private Double porcentajeSenia;

    @Min(value = 0, message = "El monto fijo no puede ser negativo")
    @Column(name = "monto_fijo_senia", nullable = true)
    private Double montoFijo;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @NotNull(message = "La fecha de fin es obligatoria")
    @Column(name = "fecha_fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne
    @JoinColumn(name = "id_complejo", nullable = false, foreignKey = @ForeignKey(name = "fk_politica_senia_complejo"))
    private ComplejoDeportivo complejoDeportivo;

    @OneToMany(mappedBy = "politicaSenia")
    private List<EspacioReservable> espacios = new ArrayList<>();
}
