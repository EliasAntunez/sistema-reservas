package com.example.tureserva.servicio;

import com.example.tureserva.modelo.ConfiguracionAlertaClima;
import com.example.tureserva.modelo.DetalleReserva;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.servicio.clima.AlertaClimaConstants;
import com.example.tureserva.servicio.clima.UmbralesBuilder;
import com.example.tureserva.servicio.dto.RespuestaClimaDTO;
import com.example.tureserva.servicio.dto.TipoPrecipitacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Servicio que encapsula la lógica de negocio de alertas climáticas.
 * Separado del scheduler para mejorar testabilidad y cohesión.
 */
@Service
public class ServicioAlertaClimatica {
    
    private static final Logger log = LoggerFactory.getLogger(ServicioAlertaClimatica.class);
    
    private final ServicioOpenMeteo servicioClima;
    private final ServicioEmail servicioEmail;
    
    public ServicioAlertaClimatica(
            ServicioOpenMeteo servicioClima,
            ServicioEmail servicioEmail) {
        this.servicioClima = servicioClima;
        this.servicioEmail = servicioEmail;
    }
    
    /**
     * Verifica clima y envía alerta si supera el umbral.
     * Usa transacción independiente para evitar rollback de todo el lote.
     * 
     * @return true si se envió alerta, false en caso contrario
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verificarYEnviarAlerta(Reserva reserva, ConfiguracionAlertaClima config) {
        try {
            // Validaciones básicas
            if (reserva.getDetalles().isEmpty()) {
                log.warn("Reserva {} sin detalles, ignorando", reserva.getId());
                return false;
            }
            
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            BigDecimal latitud = primerDetalle.getEspacioReservable()
                .getComplejoDeportivo().getLatitud();
            BigDecimal longitud = primerDetalle.getEspacioReservable()
                .getComplejoDeportivo().getLongitud();
            
            if (latitud == null || longitud == null) {
                log.warn("Complejo sin coordenadas para reserva {}", reserva.getId());
                return false;
            }
            
            LocalDateTime fechaHoraReserva = LocalDateTime.of(
                reserva.getFechaReserva(),
                primerDetalle.getHoraInicio()
            );
            
            // Consultar clima
            RespuestaClimaDTO clima = servicioClima.consultarClima(
                latitud, longitud, fechaHoraReserva, 
                config.getUmbralProbabilidad()
            );
            
            // Construir umbrales por tipo usando el builder
            Map<TipoPrecipitacion, Integer> umbrales = 
                UmbralesBuilder.fromConfiguracion(config);
            
            // Verificar si supera umbral
            Integer probabilidad = clima.getProbabilidadPrecipitacion() != null 
                ? clima.getProbabilidadPrecipitacion() 
                : 0;
            TipoPrecipitacion tipo = clima.getTipoPrecipitacion();
            
            // Si no hay tipo de precipitación (clima despejado/nublado), no enviar alerta
            if (tipo == null) {
                log.debug("No hay precipitación para reserva {}. Clima: {}", 
                    reserva.getId(), clima.getDescripcion());
                return false;
            }
            
            int umbralTipo = umbrales.getOrDefault(tipo, 
                AlertaClimaConstants.UMBRAL_PROBABILIDAD_DEFAULT);
            
            if (probabilidad < umbralTipo) {
                log.debug(AlertaClimaConstants.LOG_UMBRAL_NO_SUPERADO, 
                    reserva.getId(), probabilidad, umbralTipo, tipo);
                return false;
            }
            
            // Enviar alerta
            String descripcion = clima.isHayMalClima() 
                ? clima.getDescripcion() 
                : "Condiciones: " + clima.getDescripcion();
            
            servicioEmail.enviarAlertaClimatica(
                reserva,
                descripcion,
                probabilidad,
                tipo,
                clima.getPrecipMmHora()
            );
            
            log.info(AlertaClimaConstants.LOG_ALERTA_ENVIADA, 
                reserva.getCodigoReserva(), tipo, probabilidad);
            
            return true;
            
        } catch (Exception e) {
            log.error(AlertaClimaConstants.LOG_ERROR_ENVIO, 
                reserva.getId(), e.getMessage(), e);
            return false;
        }
    }
}
