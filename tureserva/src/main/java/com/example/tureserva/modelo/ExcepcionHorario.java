package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ENTIDAD PREPARADA PARA IMPLEMENTACIÓN FUTURA
 * 
 * Representa una excepción de horario para una fecha específica.
 * Permite manejar casos especiales como:
 * - Días feriados (cerrado)
 * - Horarios especiales (ej: 25 de diciembre cerrado)
 * - Eventos especiales (ej: torneo, horario extendido)
 * 
 * Implementar lógica en servicios y controladores cuando se requiera.
 * La validación de disponibilidad debe considerar estas excepciones.
 */
@Entity
@Table(name = "excepcion_horario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_excepcion_complejo_fecha",
                         columnNames = {"complejo_id", "fecha"})
    },
    indexes = {
        @Index(name = "idx_excepcion_fecha", columnList = "fecha"),
        @Index(name = "idx_excepcion_complejo", columnList = "complejo_id")
    }
)
@Getter @Setter @NoArgsConstructor
public class ExcepcionHorario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @NotNull(message = "La excepción debe pertenecer a un complejo deportivo")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complejo_id",
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_excepcion_complejo"))
    private ComplejoDeportivo complejoDeportivo;
    
    @NotNull(message = "La fecha de la excepción no puede ser nula")
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;
    
    @Column(name = "descripcion", length = 255)
    private String descripcion;
    
    /**
     * Si es true, el complejo está cerrado ese día (ignora horarios)
     */
    @Column(name = "cerrado", nullable = false)
    private boolean cerrado = false;
    
    /**
     * Hora de apertura especial (solo si cerrado = false)
     */
    @Column(name = "hora_apertura_especial")
    private LocalTime horaAperturaEspecial;
    
    /**
     * Hora de cierre especial (solo si cerrado = false)
     */
    @Column(name = "hora_cierre_especial")
    private LocalTime horaCierreEspecial;
    
    /**
     * Si aplica solo a un tipo de espacio (null = aplica a todo el complejo)
     */
    @Column(name = "tipo_espacio_afectado", length = 20)
    private String tipoEspacioAfectado; // "CANCHA", "SALON", o null
}
