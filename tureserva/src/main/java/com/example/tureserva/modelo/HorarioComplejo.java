package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalTime;

import com.example.tureserva.modelo.enums.DiaSemana;

/**
 * @deprecated Esta clase ha sido reemplazada por el nuevo sistema de horarios:
 * {@link ConfiguracionHorario} y {@link RangoHorario}.
 * 
 * <p>El nuevo sistema ofrece:
 * <ul>
 *   <li>Jerarquía de horarios (Master → Override → Personalizado)</li>
 *   <li>Configuraciones reutilizables</li>
 *   <li>Múltiples rangos por día (horarios partidos)</li>
 *   <li>Eliminación lógica con auditoría</li>
 * </ul>
 * 
 * <p>Esta clase se mantiene solo para compatibilidad con datos existentes.
 * No se recomienda su uso en nuevos desarrollos.
 * 
 * @see ConfiguracionHorario
 * @see RangoHorario
 */
@Deprecated(since = "2.0", forRemoval = true)
@Entity
@Table(name = "horario_complejo",
     uniqueConstraints = {@UniqueConstraint(name = "uk_horario_dia_complejo", columnNames = {"dia_semana", "complejo_id"})}
)
@Getter @Setter
public class HorarioComplejo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DiaSemana diaSemana;

    @Column(name = "hora_apertura")
    private LocalTime horaApertura;

    @Column(name = "hora_cierre") 
    private LocalTime horaCierre;

    @Column(name = "cerrado", nullable = false)
    private boolean cerrado = false;

    @ManyToOne(optional = false)
    @JoinColumn(name = "complejo_id", nullable = false, foreignKey = @ForeignKey(name = "fk_horario_complejo"))
    private ComplejoDeportivo complejoDeportivo;
}