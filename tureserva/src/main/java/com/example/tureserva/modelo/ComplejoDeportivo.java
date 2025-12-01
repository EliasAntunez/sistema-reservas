package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    /**
     * Latitud de la ubicación del complejo (coordenadas geográficas).
     * Obtenida desde la API de OpenStreetMap Nominatim.
     * Rango: -90 a +90
     */
    @Column(precision = 10, scale = 8)
    private java.math.BigDecimal latitud;

    /**
     * Longitud de la ubicación del complejo (coordenadas geográficas).
     * Obtenida desde la API de OpenStreetMap Nominatim.
     * Rango: -180 a +180
     */
    @Column(precision = 11, scale = 8)
    private java.math.BigDecimal longitud;

    @ManyToOne
    @JoinColumn(name = "administrador_id", foreignKey = @ForeignKey(name = "fk_complejo_administrador"))
    private AdministradorComplejo administradorComplejo;

    @Column(nullable = false)
    private boolean activo = true;
    
    // ==================== SISTEMA DE HORARIOS ====================
    
    /**
     * Configuración de horario MASTER (obligatoria).
     * Define el horario base del complejo que se aplica por defecto a todos los espacios.
     * Ejemplo: 15:00-00:00 (lunes a domingo)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_horario_master_id",
                foreignKey = @ForeignKey(name = "fk_complejo_horario_master"))
    private ConfiguracionHorario configuracionHorarioMaster;
    
    /**
     * Configuración de horario OVERRIDE para CANCHAS (opcional).
     * Si está presente, anula el horario master para todas las canchas del complejo.
     * Ejemplo: 18:00-00:00 (canchas abren más tarde)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_horario_canchas_id",
                foreignKey = @ForeignKey(name = "fk_complejo_horario_canchas"))
    private ConfiguracionHorario configuracionHorarioCanchas;
    
    /**
     * Configuración de horario OVERRIDE para SALONES (opcional).
     * Si está presente, anula el horario master para todos los salones del complejo.
     * Ejemplo: 15:00-21:00 (salones cierran más temprano)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_horario_salones_id",
                foreignKey = @ForeignKey(name = "fk_complejo_horario_salones"))
    private ConfiguracionHorario configuracionHorarioSalones;
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    /**
     * Obtiene la configuración de horario efectiva para un tipo de espacio.
     * Sigue la jerarquía: Override por tipo → Master
     * 
     * @param tipoEspacio "CANCHA" o "SALON"
     * @return La configuración de horario a aplicar
     */
    public ConfiguracionHorario getConfiguracionPorTipo(String tipoEspacio) {
        if (tipoEspacio == null) {
            return configuracionHorarioMaster;
        }
        
        return switch(tipoEspacio.toUpperCase()) {
            case "CANCHA" -> configuracionHorarioCanchas != null 
                ? configuracionHorarioCanchas 
                : configuracionHorarioMaster;
            case "SALON" -> configuracionHorarioSalones != null 
                ? configuracionHorarioSalones 
                : configuracionHorarioMaster;
            default -> configuracionHorarioMaster;
        };
    }
}
