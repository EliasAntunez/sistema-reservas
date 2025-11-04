package com.example.tureserva.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "espacio_politica_cancelacion", uniqueConstraints = { @UniqueConstraint(name = "uk_espacio_politica_cancelacion", columnNames = {"espacio_id", "politica_cancelacion_id"}) })
public class EspacioPoliticaCancelacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_espacio_politica_cancelacion")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "espacio_id", foreignKey = @ForeignKey(name = "fk_espacio_politica_cancelacion_espacio"), nullable = false)
    private EspacioReservable espacioReservable;

    @ManyToOne
    @JoinColumn(name = "politica_cancelacion_id", foreignKey = @ForeignKey(name = "fk_espacio_politica_cancelacion_politica_cancelacion"), nullable = false)
    private PoliticaCancelacion politicaCancelacion;

    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;
    @PrePersist
    protected void onCreate() {
        if (fechaAsignacion == null) {
            fechaAsignacion = LocalDateTime.now();
        }
    }
}
