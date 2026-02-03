/*
package com.example.tureserva.tarea;

import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.repositorio.RepositorioReserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tarea programada para actualizar automáticamente el estado de reservas vencidas y no pagadas.
 * Ejecuta cada hora y marca como FINALIZADA las reservas CONFIRMADA cuyo horario ya pasó y no tienen pago registrado.
 */

/*
@Component
public class TareaActualizarReservasVencidas {
    private static final Logger log = LoggerFactory.getLogger(TareaActualizarReservasVencidas.class);

    private final RepositorioReserva repositorioReserva;

    public TareaActualizarReservasVencidas(RepositorioReserva repositorioReserva) {
        this.repositorioReserva = repositorioReserva;
    }

    /**
     * Ejecuta cada hora para actualizar reservas vencidas y no pagadas.
     */

    /*
    @Scheduled(cron = "0 10 * * * *") // Cada hora, al minuto 10
    @Transactional
    public void actualizarReservasVencidas() {
        java.time.LocalDate fechaActual = java.time.LocalDate.now();
        java.time.LocalTime horaActual = java.time.LocalTime.now();
        log.info("[TareaActualizarReservasVencidas] Iniciando actualización de reservas vencidas a las {} {}", fechaActual, horaActual);

        // Buscar reservas CONFIRMADA cuyo horario ya pasó y no tienen pago completo registrado
        List<Reserva> reservasVencidas = repositorioReserva.findReservasVencidasSinPago(fechaActual, horaActual, EstadoReserva.CONFIRMADA);
        log.info("[TareaActualizarReservasVencidas] Se encontraron {} reservas vencidas sin pago", reservasVencidas.size());

        for (Reserva reserva : reservasVencidas) {
            reserva.setEstado(EstadoReserva.FINALIZADA);
            repositorioReserva.save(reserva);
            log.info("Reserva {} marcada como FINALIZADA (pendiente de pago)", reserva.getId());
        }
    }
}
*/