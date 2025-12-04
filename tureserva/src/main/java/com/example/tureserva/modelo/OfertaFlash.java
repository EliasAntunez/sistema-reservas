package com.example.tureserva.modelo;

import com.example.tureserva.modelo.enums.EstadoOferta;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Representa una Oferta Flash generada cuando un cliente cancela tardíamente.
 * La oferta permite recuperar el 50% de la seña si otro cliente la toma.
 * 
 * <p>Flujo de negocio:
 * 1. Cliente recibe aviso de vencimiento de plazo de cancelación gratuita
 * 2. Cliente cancela tardíamente → se genera OfertaFlash (DISPONIBLE)
 * 3. Nuevo cliente reclama la oferta → estado pasa a RECLAMADA
 * 4. Se acredita 50% de seña al cliente original
 * 5. Nueva reserva se crea con 50% de descuento
 * 
 * @author TuReserva
 */
@Entity
@Table(name = "oferta_flash",
    indexes = {
        @Index(name = "idx_oferta_token", columnList = "token"),
        @Index(name = "idx_oferta_estado", columnList = "estado"),
        @Index(name = "idx_oferta_fecha_expiracion", columnList = "fecha_expiracion")
    }
)
@Getter @Setter @NoArgsConstructor
public class OfertaFlash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Campo de versión para Optimistic Locking.
     * Hibernate lo incrementa automáticamente en cada actualización.
     * Si dos transacciones intentan modificar el mismo registro simultáneamente,
     * la segunda lanzará OptimisticLockException.
     */
    @Version
    @Column(name = "version")
    private Long version = 0L;

    /**
     * Token único para acceder a la oferta (criptográficamente seguro)
     * Se usa en el link enviado a los clientes candidatos
     */
    @NotNull(message = "El token no puede ser nulo")
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /**
     * Reserva original que fue cancelada y generó esta oferta
     */
    @NotNull(message = "La reserva original es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_original_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_oferta_reserva_original"))
    private Reserva reservaOriginal;

    /**
     * Cliente original que canceló y puede recuperar el 50% de su seña
     */
    @NotNull(message = "El cliente original es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_original_id", 
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_oferta_cliente_original"))
    private Cliente clienteOriginal;

    /**
     * Nueva reserva creada cuando la oferta es reclamada
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_nueva_id",
                foreignKey = @ForeignKey(name = "fk_oferta_reserva_nueva"))
    private Reserva reservaNueva;

    /**
     * Cliente que reclamó la oferta flash
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_reclamante_id",
                foreignKey = @ForeignKey(name = "fk_oferta_cliente_reclamante"))
    private Cliente clienteReclamante;

    /**
     * Estado actual de la oferta
     */
    @NotNull(message = "El estado no puede ser nulo")
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOferta estado = EstadoOferta.DISPONIBLE;

    /**
     * Monto original de la seña de la reserva cancelada
     */
    @NotNull(message = "El monto original es obligatorio")
    @Column(name = "monto_senia_original", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoSeniaOriginal;

    /**
     * Monto que se acreditará al cliente original (50% de la seña)
     */
    @NotNull(message = "El monto de recupero es obligatorio")
    @Column(name = "monto_recupero_cliente", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoRecuperoCliente;

    /**
     * Monto del descuento para el nuevo cliente (50% del precio total)
     */
    @NotNull(message = "El monto de descuento es obligatorio")
    @Column(name = "monto_descuento_oferta", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoDescuentoOferta;

    /**
     * Fecha y hora de creación de la oferta
     */
    @NotNull(message = "La fecha de creación es obligatoria")
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Fecha y hora de expiración de la oferta
     * Por defecto: 24 horas desde la creación
     */
    @NotNull(message = "La fecha de expiración es obligatoria")
    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    /**
     * Fecha y hora en que la oferta fue reclamada
     */
    @Column(name = "fecha_reclamacion")
    private LocalDateTime fechaReclamacion;

    /**
     * Notas adicionales sobre la oferta
     */
    @Column(name = "notas", length = 500)
    private String notas;

    // ==================== MÉTODOS DE UTILIDAD ====================

    /**
     * Genera un token criptográficamente seguro para la oferta.
     * Usa SecureRandom con 32 bytes (256 bits) de entropía,
     * codificado en Base64 URL-safe para uso en URLs.
     * 
     * @return Token generado (44 caracteres aprox)
     */
    public void generarToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[32]; // 256 bits de entropía
        secureRandom.nextBytes(randomBytes);
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Verifica si la oferta está disponible para ser reclamada
     */
    public boolean estaDisponible() {
        return estado == EstadoOferta.DISPONIBLE 
            && fechaExpiracion != null 
            && LocalDateTime.now().isBefore(fechaExpiracion);
    }

    /**
     * Verifica si la oferta expiró
     */
    public boolean haExpirado() {
        return fechaExpiracion != null 
            && LocalDateTime.now().isAfter(fechaExpiracion)
            && estado == EstadoOferta.DISPONIBLE;
    }

    /**
     * Marca la oferta como reclamada.
     * 
     * @param reclamante Cliente que reclama la oferta
     * @param nuevaReserva Nueva reserva creada
     * @throws IllegalStateException si la oferta no está disponible o si el reclamante es el cliente original
     */
    public void reclamar(Cliente reclamante, Reserva nuevaReserva) {
        if (!estaDisponible()) {
            throw new IllegalStateException("La oferta no está disponible para ser reclamada");
        }
        if (reclamante != null && reclamante.equals(this.clienteOriginal)) {
            throw new IllegalStateException("El cliente original no puede reclamar su propia oferta");
        }
        this.estado = EstadoOferta.RECLAMADA;
        this.clienteReclamante = reclamante;
        this.reservaNueva = nuevaReserva;
        this.fechaReclamacion = LocalDateTime.now();
    }

    /**
     * Marca la oferta como expirada
     */
    public void marcarComoExpirada() {
        if (estado == EstadoOferta.DISPONIBLE) {
            this.estado = EstadoOferta.EXPIRADA;
        }
    }

    /**
     * Cancela la oferta (cuando el cliente original la cancela antes de ser reclamada)
     */
    public void cancelar(String motivo) {
        if (estado != EstadoOferta.DISPONIBLE) {
            throw new IllegalStateException("Solo se pueden cancelar ofertas disponibles");
        }
        this.estado = EstadoOferta.CANCELADA;
        this.notas = motivo;
    }

    // ==================== HOOKS JPA ====================

    /**
     * Se ejecuta antes de persistir la entidad
     */
    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.token == null) {
            generarToken();
        }
        if (this.fechaExpiracion == null) {
            // Por defecto: 24 horas de vigencia
            this.fechaExpiracion = this.fechaCreacion.plusHours(24);
        }
    }
}
