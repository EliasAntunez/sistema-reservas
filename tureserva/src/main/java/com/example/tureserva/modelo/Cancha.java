package com.example.tureserva.modelo;

import com.example.tureserva.modelo.enums.TipoPiso;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cancha")
@PrimaryKeyJoinColumn(name = "espacio_reservable_id", foreignKey = @ForeignKey(name = "fk_cancha_espacio_reservable"))
@DiscriminatorValue("CANCHA")
@Getter @Setter @NoArgsConstructor
public class Cancha extends EspacioReservable {

    @Column(name = "es_techada")
    private Boolean esTechada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_piso", nullable = false, length = 50)
    private TipoPiso tipoPiso;

    @OneToMany(mappedBy = "cancha", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<CanchaDeporte> canchaDeporte = new java.util.ArrayList<>();
    
    @Override
    public String getTipoEspacio() {
        return "CANCHA";
    }
}