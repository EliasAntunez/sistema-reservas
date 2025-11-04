package com.example.tureserva.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cancha_deporte")
public class CanchaDeporte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cancha_deporte")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cancha_id", foreignKey = @ForeignKey(name = "fk_cancha_deporte_cancha"))
    private Cancha cancha;

    @ManyToOne
    @JoinColumn(name = "deporte_id", foreignKey = @ForeignKey(name = "fk_cancha_deporte_deporte"))
    private Deporte deporte;

    private LocalDateTime fechaAsignacion;

    @PrePersist
    protected void onCreate() {
        if (fechaAsignacion == null) {
            fechaAsignacion = LocalDateTime.now();
        }
    }
}
