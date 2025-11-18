package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.DetalleReserva;
import com.example.tureserva.modelo.EspacioReservable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RepositorioDetalleReserva extends JpaRepository<DetalleReserva, Long> {
    
    /**
     * Encuentra todos los detalles de reserva para un espacio en una fecha específica
     */
    List<DetalleReserva> findByEspacioReservableAndFechaReserva(EspacioReservable espacio, LocalDate fecha);
}
