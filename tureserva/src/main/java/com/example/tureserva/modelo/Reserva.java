package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.example.tureserva.modelo.enums.EstadoReserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa una reserva realizada por un cliente.
 * Una reserva puede incluir múltiples espacios y franjas horarias (DetalleReserva).
 * 
 * <p>Flujo de estados:
 * PENDIENTE → CONFIRMADA (cuando se paga la seña)
 * PENDIENTE → CANCELADA (si se cancela antes de pagar)
 * CONFIRMADA → CANCELADA (si se cancela después de pagar, aplican políticas)
 * 
 * @author TuReserva
 */
@Entity
@Table(name = "reserva",
    indexes = {
        @Index(name = "idx_reserva_cliente", columnList = "cliente_id"),
        @Index(name = "idx_reserva_fecha", columnList = "fecha_reserva"),
        @Index(name = "idx_reserva_estado", columnList = "estado")
    }
)
@Getter @Setter @NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Id publico de la reserva
     */
    @Column(name = "codigo_reserva", nullable = false, unique = true, length = 20)
    private String codigoReserva;

    /**
     * Cliente que realiza la reserva
     */
    @NotNull(message = "La reserva debe tener un cliente asociado")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_reserva_cliente"))
    private Cliente cliente;

    /**
     * Fecha de la reserva (día para el cual se reserva, no cuándo se hizo la reserva)
     */
    @NotNull(message = "La fecha de la reserva no puede ser nula")
    @Column(name = "fecha_reserva", nullable = false)
    private LocalDate fechaReserva;

    /**
     * Fecha y hora en que se creó la reserva (timestamp de creación)
     */
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Estado actual de la reserva
     */
    @NotNull(message = "El estado de la reserva no puede ser nulo")
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE;

    /**
     * Monto total de la reserva (suma de todos los detalles)
     * Se calcula automáticamente antes de persistir
     */
    @NotNull(message = "El monto total no puede ser nulo")
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto total debe ser mayor a 0")
    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    /**
     * Monto de seña pagado (puede ser null si aún no se pagó)
     */
    @DecimalMin(value = "0.0", message = "La seña no puede ser negativa")
    @Column(name = "monto_senia", precision = 10, scale = 2)
    private BigDecimal montoSenia;

    /**
     * Monto restante a pagar (montoTotal - montoSenia)
     * Se calcula automáticamente
     */
    @DecimalMin(value = "0.0", message = "El monto restante no puede ser negativo")
    @Column(name = "monto_restante", precision = 10, scale = 2)
    private BigDecimal montoRestante;

    /**
     * Notas o comentarios adicionales del cliente
     */
    @Size(max = 500, message = "Las notas no pueden exceder los 500 caracteres")
    @Column(name = "notas", length = 500)
    private String notas;

    /**
     * Motivo de cancelación (solo si estado = CANCELADA)
     */
    @Size(max = 255, message = "El motivo de cancelación no puede exceder los 255 caracteres")
    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;

    /**
     * Fecha y hora de cancelación (solo si estado = CANCELADA)
     */
    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    /**
     * Detalles de la reserva (espacios y franjas horarias reservadas)
     */
    @OneToMany(mappedBy = "reserva", 
               cascade = CascadeType.ALL, 
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<DetalleReserva> detalles = new ArrayList<>();

    /**
     * Pagos realizados para esta reserva
     * Puede tener uno o varios pagos (seña + pago completo, o solo pago completo)
     */
    @OneToMany(mappedBy = "reserva", 
               cascade = CascadeType.ALL, 
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<Pago> pagos = new ArrayList<>();

    /**
     * Indica si ya se envió una alerta climática para esta reserva.
     * Evita enviar múltiples alertas para la misma reserva.
     */
    @Column(name = "alerta_enviada", nullable = true)
    private Boolean alertaEnviada = false;
    
    /**
     * ID de la reserva original cuando esta es una reprogramación.
     * Se usa para mantener trazabilidad en caso de reprogramaciones por alertas climáticas.
     */
    @Column(name = "reserva_origen_id")
    private Long reservaOrigenId;

    // ==================== MÉTODOS DE UTILIDAD ====================

    /**
     * Agrega un detalle a la reserva
     */
    public void agregarDetalle(DetalleReserva detalle) {
        detalles.add(detalle);
        detalle.setReserva(this);
    }

    /**
     * Remueve un detalle de la reserva
     */
    public void removerDetalle(DetalleReserva detalle) {
        detalles.remove(detalle);
        detalle.setReserva(null);
    }

    /**
     * Agrega un pago a la reserva
     */
    public void agregarPago(Pago pago) {
        pagos.add(pago);
        pago.setReserva(this);
    }

    /**
     * Remueve un pago de la reserva
     */
    public void removerPago(Pago pago) {
        pagos.remove(pago);
        pago.setReserva(null);
    }

    /**
     * Calcula el monto total sumando todos los detalles
     */
    public void calcularMontoTotal() {
        this.montoTotal = detalles.stream()
            .map(DetalleReserva::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        calcularMontoRestante();
    }

    /**
     * Calcula el monto restante (total - seña)
     */
    public void calcularMontoRestante() {
        if (montoSenia == null) {
            this.montoRestante = this.montoTotal;
        } else {
            this.montoRestante = this.montoTotal.subtract(montoSenia);
        }
    }

    /**
     * Confirma la reserva (marca como CONFIRMADA)
     */
    public void confirmar() {
        if (this.estado != EstadoReserva.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden confirmar reservas en estado PENDIENTE");
        }
        this.estado = EstadoReserva.CONFIRMADA;
    }

    /**
     * Cancela la reserva
     */
    public void cancelar(String motivo) {
        if (this.estado == EstadoReserva.CANCELADA) {
            throw new IllegalStateException("La reserva ya está cancelada");
        }
        this.estado = EstadoReserva.CANCELADA;
        this.motivoCancelacion = motivo;
        this.fechaCancelacion = LocalDateTime.now();
    }

    /**
     * Verifica si la reserva está confirmada
     */
    public boolean estaConfirmada() {
        return this.estado == EstadoReserva.CONFIRMADA;
    }

    /**
     * Verifica si la reserva está cancelada
     */
    public boolean estaCancelada() {
        return this.estado == EstadoReserva.CANCELADA;
    }

    /**
     * Verifica si la reserva está pendiente
     */
    public boolean estaPendiente() {
        return this.estado == EstadoReserva.PENDIENTE;
    }

    /**
     * Calcula el subtotal de solo los espacios (sin servicios adicionales)
     */
    public BigDecimal calcularSubtotalEspacios() {
        if (detalles == null || detalles.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return detalles.stream()
            .map(detalle -> {
                if (detalle.getPrecioPorHora() == null || detalle.getDuracionHoras() == null) {
                    return BigDecimal.ZERO;
                }
                return detalle.getPrecioPorHora().multiply(detalle.getDuracionHoras());
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula el subtotal de todos los servicios adicionales
     */
    public BigDecimal calcularSubtotalServicios() {
        if (detalles == null || detalles.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return detalles.stream()
            .flatMap(detalle -> detalle.getServiciosAdicionales() != null 
                ? detalle.getServiciosAdicionales().stream() 
                : java.util.stream.Stream.empty())
            .map(servicio -> servicio.getSubtotal() != null ? servicio.getSubtotal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Verifica si la reserva requirió seña (tiene montoSenia > 0)
     */
    public boolean requirioSenia() {
        return montoSenia != null && montoSenia.compareTo(BigDecimal.ZERO) > 0;
    }

    // ==================== HOOKS JPA ====================

    /**
     * Se ejecuta antes de persistir la entidad
     */
    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        calcularMontoTotal();
        calcularMontoRestante();
    }

    /**
     * Se ejecuta antes de actualizar la entidad
     */
    @PreUpdate
    protected void onUpdate() {
        calcularMontoTotal();
        calcularMontoRestante();
    }
}
