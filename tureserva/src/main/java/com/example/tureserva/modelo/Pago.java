package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.example.tureserva.modelo.enums.TipoPago;
import com.example.tureserva.modelo.enums.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa un pago realizado para una reserva.
 * Una reserva puede tener uno o varios pagos:
 * - Sin seña: 1 pago (PAGO_COMPLETO)
 * - Con seña: 2 pagos (SENIA + PAGO_COMPLETO)
 * 
 * @author TuReserva
 */
@Entity
@Table(name = "pago",
    indexes = {
        @Index(name = "idx_pago_reserva", columnList = "reserva_id"),
        @Index(name = "idx_pago_tipo", columnList = "tipo_pago"),
        @Index(name = "idx_pago_fecha", columnList = "fecha_pago")
    }
)
@Getter @Setter @NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Reserva asociada al pago
     */
    @NotNull(message = "El pago debe estar asociado a una reserva")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_pago_reserva"))
    private Reserva reserva;

    /**
     * Tipo de pago (Seña o Pago Completo)
     */
    @NotNull(message = "El tipo de pago no puede ser nulo")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false, length = 20)
    private TipoPago tipoPago;

    /**
     * Método de pago utilizado
     */
    @NotNull(message = "El método de pago no puede ser nulo")
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 30)
    private MetodoPago metodoPago;

    /**
     * Monto del pago
     */
    @NotNull(message = "El monto del pago no puede ser nulo")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /**
     * Fecha y hora en que se realizó el pago
     */
    @NotNull(message = "La fecha del pago no puede ser nula")
    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    /**
     * ID de transacción externa (para pagos online como Mercado Pago)
     * Null para pagos manuales
     */
    @Size(max = 255, message = "El ID de transacción no puede superar 255 caracteres")
    @Column(name = "transaccion_id", length = 255)
    private String transaccionId;

    /**
     * Comprobante o número de recibo
     */
    @Size(max = 100, message = "El número de comprobante no puede superar 100 caracteres")
    @Column(name = "numero_comprobante", length = 100)
    private String numeroComprobante;

    /**
     * Usuario que registró el pago (admin/empleado)
     * Null para pagos automáticos (Mercado Pago)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por", 
                foreignKey = @ForeignKey(name = "fk_pago_usuario"))
    private Usuario registradoPor;

    /**
     * Notas o comentarios adicionales sobre el pago
     */
    @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
    @Column(name = "notas", length = 500)
    private String notas;

    /**
     * Fecha y hora de creación del registro
     */
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // ==================== MÉTODOS DE UTILIDAD ====================

    /**
     * Verifica si el pago es online (requiere integración)
     */
    public boolean esOnline() {
        return metodoPago != null && metodoPago.isOnline();
    }

    /**
     * Verifica si es un pago de seña
     */
    public boolean esSenia() {
        return tipoPago == TipoPago.SENIA;
    }

    /**
     * Verifica si es el pago completo
     */
    public boolean esPagoCompleto() {
        return tipoPago == TipoPago.PAGO_COMPLETO;
    }

    // ==================== HOOKS JPA ====================

    /**
     * Se ejecuta antes de persistir la entidad
     */
    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        
        // Si no se especificó fecha de pago, usar la actual
        if (this.fechaPago == null) {
            this.fechaPago = LocalDateTime.now();
        }
    }
}
