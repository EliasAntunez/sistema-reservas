package com.example.tureserva.evento;

import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

/**
 * Evento publicado cuando se crea una nueva reserva.
 * Se usa para registrar la auditoría después del commit de la transacción.
 */
@Getter
public class ReservaCreadaEvent {
    
    private final Long reservaId;
    private final String codigoReserva;
    private final Long complejoId;
    private final String complejoNombre;
    private final String clienteEmail;
    private final BigDecimal montoTotal;
    private final List<String> espaciosReservados;
    
    public ReservaCreadaEvent(Long reservaId, String codigoReserva, Long complejoId, 
                             String complejoNombre, String clienteEmail, 
                             BigDecimal montoTotal, List<String> espaciosReservados) {
        this.reservaId = reservaId;
        this.codigoReserva = codigoReserva;
        this.complejoId = complejoId;
        this.complejoNombre = complejoNombre;
        this.clienteEmail = clienteEmail;
        this.montoTotal = montoTotal;
        this.espaciosReservados = espaciosReservados;
    }
}
