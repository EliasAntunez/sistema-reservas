package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.DetalleReserva;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.modelo.enums.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RepositorioDetalleReserva extends JpaRepository<DetalleReserva, Long> {
    
    /**
     * Encuentra todos los detalles de reserva para un espacio en una fecha específica
     */
    List<DetalleReserva> findByEspacioReservableAndFechaReserva(EspacioReservable espacio, LocalDate fecha);

    /**
     * Encuentra todos los detalles de reserva para un espacio en una fecha específica
     * excluyendo aquellos cuya reserva esté en el estado indicado (p.ej. CANCELADA).
     */
    @Query("SELECT d FROM DetalleReserva d WHERE d.espacioReservable = :espacio AND d.fechaReserva = :fecha AND d.reserva.estado <> :estado")
    List<DetalleReserva> findByEspacioReservableAndFechaReservaAndReservaEstadoNot(
            @Param("espacio") EspacioReservable espacio,
            @Param("fecha") LocalDate fecha,
            @Param("estado") EstadoReserva estado);

        /**
         * Encuentra detalles futuros o en curso para un espacio que estén en estados activos (p.ej. PENDIENTE, CONFIRMADA).
         * Condición: fecha > hoy OR (fecha = hoy AND hora_fin > horaActual)
         */
        @Query("SELECT d FROM DetalleReserva d WHERE d.espacioReservable = :espacio AND d.reserva.estado IN :estados AND (d.fechaReserva > :hoy OR (d.fechaReserva = :hoy AND d.horaFin > :horaActual))")
        List<DetalleReserva> findActiveDetallesByEspacioFromToday(
            @Param("espacio") EspacioReservable espacio,
            @Param("hoy") LocalDate hoy,
            @Param("horaActual") java.time.LocalTime horaActual,
            @Param("estados") java.util.List<com.example.tureserva.modelo.enums.EstadoReserva> estados);
}
