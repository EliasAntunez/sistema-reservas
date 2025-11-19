package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa el detalle de una reserva: qué espacio se reservó y en qué franja horaria.
 * Cada DetalleReserva corresponde a un espacio específico en un horario específico.
 * 
 * <p>Ejemplo: Si reservo la Cancha 1 de 18:00-20:00 y el Salón A de 19:00-21:00,
 * tendré 2 DetalleReserva asociados a 1 Reserva.
 * 
 * @author TuReserva
 */
@Entity
@Table(name = "detalle_reserva",
    indexes = {
        @Index(name = "idx_detalle_reserva", columnList = "reserva_id"),
        @Index(name = "idx_detalle_espacio", columnList = "espacio_reservable_id"),
        @Index(name = "idx_detalle_fecha", columnList = "fecha_reserva")
    }
)
@Getter @Setter @NoArgsConstructor
public class DetalleReserva {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Reserva a la que pertenece este detalle
     */
    @NotNull(message = "El detalle debe pertenecer a una reserva")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_detalle_reserva"))
    private Reserva reserva;

    /**
     * Espacio reservado (puede ser Cancha o Salon)
     */
    @NotNull(message = "El detalle debe tener un espacio asignado")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "espacio_reservable_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_detalle_espacio"))
    private EspacioReservable espacioReservable;

    /**
     * Fecha de la reserva (desnormalizada desde Reserva para facilitar consultas)
     * Este campo evita tener que hacer JOIN con Reserva para filtrar por fecha
     */
    @NotNull(message = "La fecha de reserva no puede ser nula")
    @Column(name = "fecha_reserva", nullable = false)
    private java.time.LocalDate fechaReserva;

    /**
     * Hora de inicio de la reserva
     */
    @NotNull(message = "La hora de inicio no puede ser nula")
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    /**
     * Hora de fin de la reserva
     */
    @NotNull(message = "La hora de fin no puede ser nula")
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /**
     * Precio por hora del espacio en el momento de la reserva
     * Se guarda para mantener el precio histórico aunque luego cambie
     */
    @NotNull(message = "El precio por hora no puede ser nulo")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Column(name = "precio_por_hora", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioPorHora;

    /**
     * Duración de la reserva en horas (calculado)
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "La duración debe ser mayor a 0")
    @Column(name = "duracion_horas", nullable = false, precision = 5, scale = 2)
    private BigDecimal duracionHoras;

    /**
     * Subtotal de este detalle (precioPorHora * duracionHoras)
     * Se calcula automáticamente
     */
    @NotNull(message = "El subtotal no puede ser nulo")
    @DecimalMin(value = "0.0", inclusive = false, message = "El subtotal debe ser mayor a 0")
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    // ==================== MÉTODOS DE UTILIDAD ====================

    /**
     * Calcula la duración en horas entre horaInicio y horaFin
     */
    public void calcularDuracion() {
        if (horaInicio != null && horaFin != null) {
            Duration duration = Duration.between(horaInicio, horaFin);
            
            // Si horaFin es menor que horaInicio, significa que cruza medianoche
            if (horaFin.isBefore(horaInicio)) {
                duration = duration.plusDays(1);
            }
            
            // Convertir a horas decimales (ej: 1.5 horas)
            double horas = duration.toMinutes() / 60.0;
            this.duracionHoras = BigDecimal.valueOf(horas);
        }
    }

    /**
     * Calcula el subtotal (precioPorHora * duracionHoras)
     */
    public void calcularSubtotal() {
        if (precioPorHora != null && duracionHoras != null) {
            this.subtotal = precioPorHora.multiply(duracionHoras);
        } else {
            this.subtotal = java.math.BigDecimal.ZERO;
        }

        // Incluir el subtotal de los servicios adicionales asociados a este detalle
        try {
            java.math.BigDecimal serviciosSum = serviciosAdicionales.stream()
                    .map(s -> s.getSubtotal() == null ? java.math.BigDecimal.ZERO : s.getSubtotal())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            this.subtotal = this.subtotal.add(serviciosSum);
        } catch (Exception ex) {
            // En caso de que la colección aún no esté inicializada o haya otro problema,
            // no queremos romper la validación; dejamos el subtotal como estaba.
        }
    }

    /**
     * Valida que el horario sea coherente
     */
    public void validarHorario() {
        if (horaInicio != null && horaFin != null) {
            // Permitir rangos que cruzan medianoche (22:00-02:00)
            // pero validar que no sean iguales
            if (horaInicio.equals(horaFin)) {
                throw new IllegalArgumentException(
                    "La hora de inicio y fin no pueden ser iguales");
            }
        }
    }

    /**
     * Verifica si este detalle solapa con otro en el mismo espacio
     */
    public boolean solapaConHorario(LocalTime otroInicio, LocalTime otroFin) {
        // Caso 1: Ninguno cruza medianoche
        if (!cruzaMedianoche() && !cruzaMedianoche(otroInicio, otroFin)) {
            return this.horaInicio.isBefore(otroFin) && otroInicio.isBefore(this.horaFin);
        }
        
        // Caso 2: Este cruza medianoche
        if (cruzaMedianoche() && !cruzaMedianoche(otroInicio, otroFin)) {
            return otroInicio.isBefore(this.horaFin) || otroFin.isAfter(this.horaInicio);
        }
        
        // Caso 3: Otro cruza medianoche
        if (!cruzaMedianoche() && cruzaMedianoche(otroInicio, otroFin)) {
            return this.horaInicio.isBefore(otroFin) || this.horaFin.isAfter(otroInicio);
        }
        
        // Caso 4: Ambos cruzan medianoche - siempre solapan
        return true;
    }

    /**
     * Verifica si este detalle cruza la medianoche
     */
    private boolean cruzaMedianoche() {
        return horaFin.isBefore(horaInicio);
    }

    /**
     * Verifica si un rango horario cruza la medianoche
     */
    private boolean cruzaMedianoche(LocalTime inicio, LocalTime fin) {
        return fin.isBefore(inicio);
    }

    // ==================== HOOKS JPA ====================

    /**
     * Servicios adicionales asociados a este detalle (por ejemplo: mantención, árbitro, alquiler de equipamiento).
     * Se modelan a nivel de detalle porque aplican a un espacio y franja horaria concreta.
     */
    @OneToMany(mappedBy = "detalleReserva", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DetalleServicioAdicional> serviciosAdicionales = new ArrayList<>();

    // Helper methods to manage bidirectional relationship
    public void agregarServicioAdicional(DetalleServicioAdicional servicio) {
        if (servicio == null) return;
        serviciosAdicionales.add(servicio);
        servicio.setDetalleReserva(this);
    }

    public void removerServicioAdicional(DetalleServicioAdicional servicio) {
        if (servicio == null) return;
        serviciosAdicionales.remove(servicio);
        servicio.setDetalleReserva(null);
    }

    /**
     * Se ejecuta antes de persistir o actualizar
     */
    @PrePersist
    @PreUpdate
    protected void onSave() {
        validarHorario();
        validarHoraFutura();
        calcularDuracion();
        calcularSubtotal();
        
        // Copiar fecha de la reserva padre si no está seteada
        if (fechaReserva == null && reserva != null) {
            this.fechaReserva = reserva.getFechaReserva();
        }
    }
    
    /**
     * Valida que la reserva sea al menos 1 hora en el futuro
     */
    private void validarHoraFutura() {
        if (fechaReserva != null && horaInicio != null) {
            LocalDateTime ahora = LocalDateTime.now();
            LocalDateTime inicioReserva = LocalDateTime.of(fechaReserva, horaInicio);
            
            if (inicioReserva.isBefore(ahora.plusHours(1))) {
                throw new IllegalStateException(
                    "La reserva debe realizarse con al menos 1 hora de anticipación. " +
                    "Hora actual: " + ahora.toLocalTime().withSecond(0).withNano(0) + 
                    ", Hora de reserva: " + horaInicio
                );
            }
        }
    }
}
