package com.example.tureserva.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "espacio_politica_senia", uniqueConstraints = { @UniqueConstraint(name = "uk_espacio_politica_senia", columnNames = {"espacio_id", "politica_senia_id"}) })
public class EspacioPoliticaSenia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_espacio_politica_senia")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "espacio_id", foreignKey = @ForeignKey(name = "fk_espacio_politica_senia_espacio"), nullable = false)
    private EspacioReservable espacioReservable;

    @ManyToOne
    @JoinColumn(name = "politica_senia_id", foreignKey = @ForeignKey(name = "fk_espacio_politica_senia_politica_senia"), nullable = false)
    private PoliticaSenia politicaSenia;

    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @PrePersist
    protected void onCreate() {
        if (fechaAsignacion == null) {
            fechaAsignacion = LocalDateTime.now();
        }
    }
}
