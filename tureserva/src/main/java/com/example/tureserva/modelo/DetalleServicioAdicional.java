package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Representa la selección de un servicio adicional dentro de un detalle de reserva.
 * Se asocia a un `ServicioAdicional` y a un `DetalleReserva` (uno a muchos desde DetalleReserva).
 */
@Entity
@Table(
	name = "detalle_servicio_adicional",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_detalle_servicio_unico", columnNames = {"detalle_reserva_id", "servicio_adicional_id"})
	},
	indexes = {
		@Index(name = "idx_detalle_servicio_detalle", columnList = "detalle_reserva_id"),
		@Index(name = "idx_detalle_servicio_servicio", columnList = "servicio_adicional_id")
	}
)
@Getter
@Setter
@NoArgsConstructor
public class DetalleServicioAdicional implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "detalle_reserva_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detalle_servicio_detalle_reserva"))
	private DetalleReserva detalleReserva;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "servicio_adicional_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detalle_servicio_servicio_adicional"))
	private ServicioAdicional servicioAdicional;

	@NotNull
	@Min(1)
	@Column(name = "cantidad", nullable = false)
	private Integer cantidad = 1;

	@NotNull
	@Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
	private BigDecimal precioUnitario;

	@NotNull
	@Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal = BigDecimal.ZERO;

	// ==================== UTILITIES ====================

	public void calcularSubtotal() {
		if (precioUnitario != null && cantidad != null) {
			this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
		} else {
			this.subtotal = BigDecimal.ZERO;
		}
	}

	@PrePersist
	@PreUpdate
	protected void onSave() {
		if (precioUnitario != null) {
			precioUnitario = precioUnitario.setScale(2, RoundingMode.HALF_UP);
		}
		if (cantidad == null) {
			cantidad = 1;
		}
		calcularSubtotal();
	}
}
