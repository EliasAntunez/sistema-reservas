package com.example.tureserva.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Configuración de alertas climáticas para un complejo deportivo.
 * Determina cuándo y cómo notificar a los clientes sobre condiciones climáticas adversas.
 */
@Entity
@Table(name = "configuracion_alerta_clima")
@Getter
@Setter
public class ConfiguracionAlertaClima {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Complejo deportivo al que pertenece esta configuración.
     * Relación OneToOne: cada complejo tiene una única configuración de alertas.
     */
    @OneToOne
    @JoinColumn(name = "complejo_id", nullable = false, unique = true,
                foreignKey = @ForeignKey(name = "fk_alerta_complejo"))
    private ComplejoDeportivo complejo;
    
    /**
     * Estrategia de notificación: HORAS_ANTES o HORARIO_FIJO.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstrategiaAlerta estrategia;
    
    /**
     * Valor numérico para la estrategia HORAS_ANTES.
     * Representa cuántas horas antes de la reserva se debe enviar la alerta.
     * Ejemplo: 24 = alertar 24 horas antes del partido.
     * NULL si estrategia es HORARIO_FIJO.
     */
    @Column(name = "horas_anticipacion")
    private Integer horasAnticipacion;
    
    /**
     * Hora fija para la estrategia HORARIO_FIJO.
     * Representa a qué hora del día se debe revisar el clima.
     * Ejemplo: 06:00 = revisar clima a las 6 AM del día de la reserva.
     * NULL si estrategia es HORAS_ANTES.
     */
    @Column(name = "horario_fijo")
    private LocalTime horarioFijo;
    
    /**
     * Umbral de probabilidad de lluvia para considerar "mal clima".
     * Valor de 0 a 100.
     * Ejemplo: 50 = alertar si probabilidad de lluvia > 50%.
     */
    @Column(name = "umbral_probabilidad", nullable = false)
    private Integer umbralProbabilidad = 50;

    /**
     * Umbrales por tipo de precipitación (opcionales). Si son NULL, se
     * usa el valor genérico `umbralProbabilidad`.
     */
    @Column(name = "umbral_llovizna")
    private Integer umbralLlovizna;

    @Column(name = "umbral_lluvia")
    private Integer umbralLluvia;

    @Column(name = "umbral_chubascos")
    private Integer umbralChubascos;

    @Column(name = "umbral_tormenta")
    private Integer umbralTormenta;

    @Column(name = "umbral_nieve")
    private Integer umbralNieve;

    @Column(name = "umbral_otro")
    private Integer umbralOtro;
    
    /**
     * Indica si el sistema de alertas está activo para este complejo.
     */
    @Column(nullable = false)
    private Boolean activo = true;
    
    /**
     * Valida que la configuración sea coherente según la estrategia elegida.
     */
    @PrePersist
    @PreUpdate
    private void validarConfiguracion() {
        if (estrategia == EstrategiaAlerta.HORAS_ANTES) {
            if (horasAnticipacion == null || horasAnticipacion <= 0) {
                throw new IllegalStateException("HORAS_ANTES requiere un valor de horasAnticipacion > 0");
            }
            horarioFijo = null; // Limpiar horario fijo si no aplica
        } else if (estrategia == EstrategiaAlerta.HORARIO_FIJO) {
            if (horarioFijo == null) {
                throw new IllegalStateException("HORARIO_FIJO requiere un horarioFijo válido");
            }
            horasAnticipacion = null; // Limpiar horas si no aplica
        }
        
        if (umbralProbabilidad == null || umbralProbabilidad < 0 || umbralProbabilidad > 100) {
            throw new IllegalStateException("umbralProbabilidad debe estar entre 0 y 100");
        }

        // Validar umbrales por tipo si fueron provistos
        if (umbralLlovizna != null && (umbralLlovizna < 0 || umbralLlovizna > 100)) {
            throw new IllegalStateException("umbralLlovizna debe estar entre 0 y 100");
        }
        if (umbralLluvia != null && (umbralLluvia < 0 || umbralLluvia > 100)) {
            throw new IllegalStateException("umbralLluvia debe estar entre 0 y 100");
        }
        if (umbralChubascos != null && (umbralChubascos < 0 || umbralChubascos > 100)) {
            throw new IllegalStateException("umbralChubascos debe estar entre 0 y 100");
        }
        if (umbralTormenta != null && (umbralTormenta < 0 || umbralTormenta > 100)) {
            throw new IllegalStateException("umbralTormenta debe estar entre 0 y 100");
        }
        if (umbralNieve != null && (umbralNieve < 0 || umbralNieve > 100)) {
            throw new IllegalStateException("umbralNieve debe estar entre 0 y 100");
        }
        if (umbralOtro != null && (umbralOtro < 0 || umbralOtro > 100)) {
            throw new IllegalStateException("umbralOtro debe estar entre 0 y 100");
        }
    }
}
