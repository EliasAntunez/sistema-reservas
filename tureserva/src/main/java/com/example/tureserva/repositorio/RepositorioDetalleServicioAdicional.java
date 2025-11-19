package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.DetalleServicioAdicional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioDetalleServicioAdicional extends JpaRepository<DetalleServicioAdicional, Long> {

    @Query("SELECT s FROM DetalleServicioAdicional s " +
           "LEFT JOIN FETCH s.servicioAdicional sa " +
           "LEFT JOIN FETCH s.detalleReserva d " +
           "WHERE d.reserva.id = :reservaId")
    List<DetalleServicioAdicional> findByReservaIdWithServicioAdicional(@Param("reservaId") Long reservaId);

        @Query("SELECT COALESCE(SUM(s.cantidad), 0) FROM DetalleServicioAdicional s " +
            "JOIN s.detalleReserva d " +
            "JOIN d.reserva r " +
            "WHERE s.servicioAdicional.id = :svcId " +
            "  AND d.fechaReserva = :fecha " +
            "  AND r.estado <> com.example.tureserva.modelo.enums.EstadoReserva.CANCELADA " +
            "  AND (d.horaInicio < :horaFin AND d.horaFin > :horaInicio)")
        Integer sumCantidadParaServicioEnFranja(@Param("svcId") Long svcId,
                             @Param("fecha") java.time.LocalDate fecha,
                             @Param("horaInicio") java.time.LocalTime horaInicio,
                             @Param("horaFin") java.time.LocalTime horaFin);
}
