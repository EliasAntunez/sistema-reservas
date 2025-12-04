package com.example.tureserva.modelo;

import com.example.tureserva.modelo.enums.TipoMovimiento;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa un movimiento individual en la cuenta corriente de un cliente.
 * Registra cada acreditación o débito para mantener trazabilidad.
 * 
 * @author TuReserva
 */
@Entity
@Table(name = "movimiento_cuenta",
    indexes = {
        @Index(name = "idx_movimiento_cuenta", columnList = "cuenta_corriente_id"),
        @Index(name = "idx_movimiento_fecha", columnList = "fecha_movimiento"),
        @Index(name = "idx_movimiento_oferta", columnList = "oferta_flash_id")
    }
)
@Getter @Setter @NoArgsConstructor
public class MovimientoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Cuenta corriente asociada
     */
    @NotNull(message = "La cuenta es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_corriente_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_movimiento_cuenta"))
    private CuentaCorriente cuentaCorriente;

    /**
     * Tipo de movimiento (crédito o débito)
     */
    @NotNull(message = "El tipo de movimiento es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 20)
    private TipoMovimiento tipoMovimiento;

    /**
     * Monto del movimiento
     */
    @NotNull(message = "El monto es obligatorio")
    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /**
     * Saldo anterior antes del movimiento
     */
    @NotNull(message = "El saldo anterior es obligatorio")
    @Column(name = "saldo_anterior", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoAnterior;

    /**
     * Saldo nuevo después del movimiento
     */
    @NotNull(message = "El saldo nuevo es obligatorio")
    @Column(name = "saldo_nuevo", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoNuevo;

    /**
     * Descripción del movimiento
     */
    @NotNull(message = "La descripción es obligatoria")
    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    /**
     * Oferta Flash relacionada (si aplica)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oferta_flash_id",
                foreignKey = @ForeignKey(name = "fk_movimiento_oferta"))
    private OfertaFlash ofertaFlash;

    /**
     * Reserva relacionada (si aplica)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id",
                foreignKey = @ForeignKey(name = "fk_movimiento_reserva"))
    private Reserva reserva;

    /**
     * Fecha y hora del movimiento
     */
    @NotNull(message = "La fecha es obligatoria")
    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDateTime fechaMovimiento;

    // ==================== HOOKS JPA ====================

    /**
     * Se ejecuta antes de persistir la entidad
     */
    @PrePersist
    protected void onCreate() {
        if (this.fechaMovimiento == null) {
            this.fechaMovimiento = LocalDateTime.now();
        }
    }
}
