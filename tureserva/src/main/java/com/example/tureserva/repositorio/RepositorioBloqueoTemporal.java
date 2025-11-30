package com.example.tureserva.repositorio;

import com.example.tureserva.modelo.BloqueoTemporal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface RepositorioBloqueoTemporal extends JpaRepository<BloqueoTemporal, Long> {

    /**
     * Busca bloqueos activos (no expirados) para un espacio, fecha y rango horario.
     * Usa superposición de intervalos: (inicio1 < fin2) AND (fin1 > inicio2)
     */
    @Query("SELECT b FROM BloqueoTemporal b WHERE " +
           "b.espacio.id = :espacioId AND " +
           "b.fechaReserva = :fecha AND " +
           "b.horaInicio < :horaFin AND " +
           "b.horaFin > :horaInicio AND " +
           "b.expiraEn > :ahora")
    List<BloqueoTemporal> findBloqueosActivosEnRango(
        @Param("espacioId") Long espacioId,
        @Param("fecha") LocalDate fecha,
        @Param("horaInicio") LocalTime horaInicio,
        @Param("horaFin") LocalTime horaFin,
        @Param("ahora") LocalDateTime ahora
    );

    /**
     * Busca bloqueo por ID de pago
     */
    @Query("SELECT b FROM BloqueoTemporal b WHERE b.pagoId = :pagoId")
    List<BloqueoTemporal> findByPagoId(@Param("pagoId") Long pagoId);

    /**
     * Elimina bloqueos expirados (tarea de limpieza)
     */
    @Modifying
    @Query("DELETE FROM BloqueoTemporal b WHERE b.expiraEn < :ahora")
    int eliminarBloqueosExpirados(@Param("ahora") LocalDateTime ahora);

    /**
     * Elimina bloqueos asociados a un pago (cuando se crea la reserva)
     */
    @Modifying
    @Query("DELETE FROM BloqueoTemporal b WHERE b.pagoId = :pagoId")
    int eliminarPorPagoId(@Param("pagoId") Long pagoId);
}
