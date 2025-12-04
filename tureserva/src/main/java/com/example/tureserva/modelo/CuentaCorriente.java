package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa la cuenta corriente de un cliente para gestionar créditos.
 * Almacena el saldo disponible que el cliente puede usar para futuras reservas.
 * 
 * <p>Casos de uso:
 * - Recupero de seña por venta de Oferta Flash (50%)
 * - Futuros créditos promocionales
 * - Reembolsos parciales
 * 
 * @author TuReserva
 */
@Entity
@Table(name = "cuenta_corriente",
    indexes = {
        @Index(name = "idx_cuenta_cliente", columnList = "cliente_id"),
        @Index(name = "idx_cuenta_saldo", columnList = "saldo_disponible")
    }
)
@Getter @Setter @NoArgsConstructor
public class CuentaCorriente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Cliente dueño de esta cuenta
     * Relación 1:1 (un cliente tiene una cuenta corriente)
     */
    @NotNull(message = "El cliente es obligatorio")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", 
                nullable = false, 
                unique = true,
                foreignKey = @ForeignKey(name = "fk_cuenta_cliente"))
    private Cliente cliente;

    /**
     * Saldo disponible en créditos
     */
    @NotNull(message = "El saldo no puede ser nulo")
    @Column(name = "saldo_disponible", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoDisponible = BigDecimal.ZERO;

    /**
     * Fecha de creación de la cuenta
     */
    @NotNull(message = "La fecha de creación es obligatoria")
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Última fecha de actualización del saldo
     */
    @Column(name = "fecha_ultima_actualizacion")
    private LocalDateTime fechaUltimaActualizacion;

    // ==================== MÉTODOS DE UTILIDAD ====================

    /**
     * Acredita un monto a la cuenta
     */
    public void acreditar(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a acreditar debe ser mayor a cero");
        }
        this.saldoDisponible = this.saldoDisponible.add(monto);
        this.fechaUltimaActualizacion = LocalDateTime.now();
    }

    /**
     * Debita un monto de la cuenta
     * @throws IllegalStateException si no hay saldo suficiente
     */
    public void debitar(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a debitar debe ser mayor a cero");
        }
        if (this.saldoDisponible.compareTo(monto) < 0) {
            throw new IllegalStateException("Saldo insuficiente");
        }
        this.saldoDisponible = this.saldoDisponible.subtract(monto);
        this.fechaUltimaActualizacion = LocalDateTime.now();
    }

    /**
     * Verifica si hay saldo suficiente
     */
    public boolean tieneSaldoSuficiente(BigDecimal monto) {
        return saldoDisponible != null 
            && monto != null 
            && saldoDisponible.compareTo(monto) >= 0;
    }

    // ==================== HOOKS JPA ====================

    /**
     * Se ejecuta antes de persistir la entidad
     */
    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaUltimaActualizacion = LocalDateTime.now();
        if (this.saldoDisponible == null) {
            this.saldoDisponible = BigDecimal.ZERO;
        }
    }
}
