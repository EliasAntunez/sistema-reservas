package com.example.tureserva.modelo;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "salon")
@PrimaryKeyJoinColumn(name = "espacio_reservable_id", foreignKey = @ForeignKey(name = "fk_salon_espacio_reservable"))
@DiscriminatorValue("SALON")
@Getter @Setter @NoArgsConstructor
public class Salon extends EspacioReservable {

    @Min(1)
    @Column(name = "metros_cuadrados", nullable = false)
    private int metrosCuadrados;

    @Column(name = "esta_climatizado", nullable = false)
    private boolean estaClimatizado;
}
