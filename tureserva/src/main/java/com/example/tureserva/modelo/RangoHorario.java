package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Representa un rango horario específico para un día de la semana.
 * Pertenece a una ConfiguracionHorario.
 * 
 * Validaciones:
 * - No permite solapamiento de horarios para el mismo día en la misma configuración
 * - Permite múltiples rangos por día (para horarios partidos: ej. 9-12 y 16-20)
 * - Permite rangos que cruzan medianoche (ej. 22:00-01:00)
 */
@Entity
@Table(name = "rango_horario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rango_config_dia_horas",
                         columnNames = {"configuracion_id", "dia_semana", "hora_apertura", "hora_cierre"})
    },
    indexes = {
        @Index(name = "idx_rango_configuracion", columnList = "configuracion_id"),
        @Index(name = "idx_rango_dia", columnList = "dia_semana")
    }
)
@Getter @Setter @NoArgsConstructor
public class RangoHorario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @NotNull(message = "El día de la semana no puede ser nulo")
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;
    
    @NotNull(message = "La hora de apertura no puede ser nula")
    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;
    
    @NotNull(message = "La hora de cierre no puede ser nula")
    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;
    
    @NotNull(message = "El rango debe pertenecer a una configuración de horario")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuracion_id",
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_rango_configuracion"))
    private ConfiguracionHorario configuracionHorario;
    
    // Métodos de utilidad
    
    /**
     * Verifica si este rango cruza la medianoche (ej: 22:00-01:00)
     */
    public boolean cruzaMedianoche() {
        return horaCierre.isBefore(horaApertura);
    }
    
    /**
     * Verifica si hay solapamiento con otro rango del mismo día.
     * Considera rangos que cruzan medianoche.
     */
    public boolean solapa(RangoHorario otro) {
        if (!this.diaSemana.equals(otro.diaSemana)) {
            return false;
        }
        
        // Caso 1: Ninguno cruza medianoche
        if (!this.cruzaMedianoche() && !otro.cruzaMedianoche()) {
            return this.horaApertura.isBefore(otro.horaCierre) 
                && otro.horaApertura.isBefore(this.horaCierre);
        }
        
        // Caso 2: Este cruza medianoche
        if (this.cruzaMedianoche() && !otro.cruzaMedianoche()) {
            return otro.horaApertura.isBefore(this.horaCierre) 
                || otro.horaCierre.isAfter(this.horaApertura);
        }
        
        // Caso 3: Otro cruza medianoche
        if (!this.cruzaMedianoche() && otro.cruzaMedianoche()) {
            return this.horaApertura.isBefore(otro.horaCierre) 
                || this.horaCierre.isAfter(otro.horaApertura);
        }
        
        // Caso 4: Ambos cruzan medianoche - siempre hay solapamiento
        return true;
    }
    
    /**
     * Valida que el rango sea coherente
     */
    @PrePersist
    @PreUpdate
    private void validar() {
        if (horaApertura != null && horaCierre != null) {
            if (horaApertura.equals(horaCierre)) {
                throw new IllegalStateException(
                    "La hora de apertura y cierre no pueden ser iguales");
            }
        }
    }
}
