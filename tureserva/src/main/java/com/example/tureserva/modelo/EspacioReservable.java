package com.example.tureserva.modelo;

import com.example.tureserva.modelo.enums.EstadoOperativo;
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

    // Relación Many-to-One: Muchos espacios pueden tener la misma política de seña
    @ManyToOne
    @JoinColumn(name = "politica_senia_id", foreignKey = @ForeignKey(name = "fk_espacio_politica_senia"))
    private PoliticaSenia politicaSenia;

    // Relación Many-to-One: Muchos espacios pueden tener la misma política de cancelación
    @ManyToOne
    @JoinColumn(name = "politica_cancelacion_id", foreignKey = @ForeignKey(name = "fk_espacio_politica_cancelacion"))
    private PoliticaCancelacion politicaCancelacion;
    
    // ==================== NUEVO SISTEMA DE HORARIOS ====================
    
    /**
     * Estado operativo del espacio individual.
     * Controla si el espacio está disponible para reservas independientemente del horario.
     */
    @NotNull(message = "El estado operativo no puede ser nulo")
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_operativo", nullable = false, length = 20)
    private EstadoOperativo estadoOperativo = EstadoOperativo.DISPONIBLE;
    
    /**
     * Configuración de horario PERSONALIZADA para este espacio específico (opcional).
     * Tiene MÁXIMA PRIORIDAD. Si está presente, anula tanto el override por tipo
     * como el horario master del complejo.
     * 
     * Ejemplo: Una cancha que solo abre los fines de semana puede tener su propio horario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horario_personalizado_id",
                foreignKey = @ForeignKey(name = "fk_espacio_horario_personalizado"))
    private ConfiguracionHorario horarioPersonalizado;
    
    // ==================== MÉTODOS DE LÓGICA DE NEGOCIO ====================
    
    /**
     * Método abstracto que debe ser implementado por las subclases.
     * Retorna el tipo de espacio para la lógica de herencia de horarios.
     * 
     * @return "CANCHA" o "SALON"
     */
    public abstract String getTipoEspacio();
    
    /**
     * Obtiene la configuración de horario efectiva para este espacio.
     * Sigue la jerarquía de prioridades:
     * 1. Horario Personalizado del espacio (máxima prioridad)
     * 2. Horario Override por tipo (del complejo)
     * 3. Horario Master del complejo (fallback siempre presente)
     * 
     * @return La configuración de horario a aplicar
     */
    public ConfiguracionHorario getConfiguracionHorarioEfectiva() {
        // 1. Si tiene horario personalizado, tiene máxima prioridad
        if (horarioPersonalizado != null) {
            return horarioPersonalizado;
        }
        
        // 2. Si no, buscar por tipo en el complejo (puede ser override o master)
        if (complejoDeportivo != null) {
            return complejoDeportivo.getConfiguracionPorTipo(getTipoEspacio());
        }
        
        // 3. Fallback: horario master del complejo
        return complejoDeportivo != null ? complejoDeportivo.getConfiguracionHorarioMaster() : null;
    }
    
    /**
     * Verifica si este espacio está disponible para hacer reservas.
     * Solo verifica el estado operativo, NO el horario.
     * 
     * @return true si el espacio puede recibir reservas
     */
    public boolean estaDisponibleParaReservas() {
        return activo && estadoOperativo.isDisponibleParaReservas();
    }
}

