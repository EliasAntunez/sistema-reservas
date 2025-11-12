package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de horarios reutilizable que pertenece a un ComplejoDeportivo.
 * Puede ser usada como:
 * - Horario Master del complejo (obligatorio)
 * - Horario Override para un tipo de espacio (Cancha/Salon)
 * - Horario Personalizado para un EspacioReservable individual
 */
@Entity
@Table(name = "configuracion_horario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_config_nombre_complejo", 
                         columnNames = {"nombre", "complejo_id"})
    },
    indexes = {
        @Index(name = "idx_config_activo", columnList = "activo"),
        @Index(name = "idx_config_complejo", columnList = "complejo_id")
    }
)
@Getter @Setter @NoArgsConstructor
public class ConfiguracionHorario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @NotBlank(message = "El nombre de la configuración no puede estar vacío")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;
    
    @Size(max = 255, message = "La descripción no puede exceder los 255 caracteres")
    @Column(name = "descripcion", length = 255)
    private String descripcion;
    
    @NotNull(message = "La configuración debe pertenecer a un complejo deportivo")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complejo_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_config_horario_complejo"))
    private ComplejoDeportivo complejoDeportivo;
    
    @OneToMany(mappedBy = "configuracionHorario", 
               cascade = CascadeType.ALL, 
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<RangoHorario> rangosHorario = new ArrayList<>();
    
    // Campos para eliminación lógica (soft delete)
    // Agregar índice en 'activo' para optimizar queries de filtrado
    
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
    
    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;
    
    @Size(max = 500, message = "El motivo de eliminación no puede exceder los 500 caracteres")
    @Column(name = "motivo_eliminacion", length = 500)
    private String motivoEliminacion;
    
    // Métodos de utilidad
    
    /**
     * Agrega un rango horario a esta configuración
     */
    public void agregarRango(RangoHorario rango) {
        rangosHorario.add(rango);
        rango.setConfiguracionHorario(this);
    }
    
    /**
     * Remueve un rango horario de esta configuración
     */
    public void removerRango(RangoHorario rango) {
        rangosHorario.remove(rango);
        rango.setConfiguracionHorario(null);
    }
    
    /**
     * Marca esta configuración como eliminada lógicamente
     */
    public void eliminarLogicamente(String motivo) {
        this.activo = false;
        this.fechaEliminacion = LocalDateTime.now();
        this.motivoEliminacion = motivo;
    }
    
    /**
     * Restaura una configuración eliminada lógicamente
     */
    public void restaurar() {
        this.activo = true;
        this.fechaEliminacion = null;
        this.motivoEliminacion = null;
    }
    
    /**
     * Verifica si está activa
     */
    public boolean estaActiva() {
        return activo != null && activo;
    }
}
