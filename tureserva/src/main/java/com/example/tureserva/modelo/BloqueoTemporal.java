package com.example.tureserva.modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Representa un bloqueo temporal de un espacio mientras un cliente
 * está en proceso de pago. Evita que múltiples usuarios paguen
 * simultáneamente por el mismo horario.
 */
@Entity
@Table(name = "bloqueo_temporal", indexes = {
    @Index(name = "idx_bloqueo_expiracion", columnList = "expira_en"),
    @Index(name = "idx_bloqueo_espacio_fecha", columnList = "espacio_id,fecha_reserva")
})
public class BloqueoTemporal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "espacio_id", nullable = false)
    private EspacioReservable espacio;

    @Column(name = "fecha_reserva", nullable = false)
    private LocalDate fechaReserva;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "cliente_email", nullable = false)
    private String clienteEmail;

    @Column(name = "pago_id")
    private Long pagoId;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    // Constructor vacío para JPA
    public BloqueoTemporal() {
        this.creadoEn = LocalDateTime.now();
    }

    // Constructor con parámetros
    public BloqueoTemporal(EspacioReservable espacio, LocalDate fechaReserva, 
                          LocalTime horaInicio, LocalTime horaFin, 
                          String clienteEmail, Long pagoId) {
        this.espacio = espacio;
        this.fechaReserva = fechaReserva;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.clienteEmail = clienteEmail;
        this.pagoId = pagoId;
        this.creadoEn = LocalDateTime.now();
        // Expira en 10 minutos (tiempo suficiente para completar el pago)
        this.expiraEn = LocalDateTime.now().plusMinutes(10);
    }

    /**
     * Verifica si el bloqueo ha expirado
     */
    public boolean haExpirado() {
        return LocalDateTime.now().isAfter(expiraEn);
    }

    /**
     * Verifica si este bloqueo se superpone con un rango horario dado
     */
    public boolean seSuperponeCon(LocalTime inicio, LocalTime fin) {
        return horaInicio.isBefore(fin) && horaFin.isAfter(inicio);
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EspacioReservable getEspacio() {
        return espacio;
    }

    public void setEspacio(EspacioReservable espacio) {
        this.espacio = espacio;
    }

    public LocalDate getFechaReserva() {
        return fechaReserva;
    }

    public void setFechaReserva(LocalDate fechaReserva) {
        this.fechaReserva = fechaReserva;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public void setClienteEmail(String clienteEmail) {
        this.clienteEmail = clienteEmail;
    }

    public Long getPagoId() {
        return pagoId;
    }

    public void setPagoId(Long pagoId) {
        this.pagoId = pagoId;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(LocalDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }
}
