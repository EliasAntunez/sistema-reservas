package com.example.tureserva.tarea;

import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.repositorio.RepositorioConfiguracionAlertaClima;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.ServicioEmail;
import com.example.tureserva.servicio.ServicioOpenMeteo;
import com.example.tureserva.servicio.dto.RespuestaClimaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Tarea programada que ejecuta cada hora para verificar si hay reservas
 * que requieren alertas climáticas según la configuración de cada complejo.
 */
@Component
public class AlertaClimaticaScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(AlertaClimaticaScheduler.class);
    
    private final RepositorioConfiguracionAlertaClima repositorioConfigAlerta;
    private final RepositorioReserva repositorioReserva;
    private final ServicioOpenMeteo servicioClima;
    private final ServicioEmail servicioEmail;
    
    public AlertaClimaticaScheduler(
            RepositorioConfiguracionAlertaClima repositorioConfigAlerta,
            RepositorioReserva repositorioReserva,
            ServicioOpenMeteo servicioClima,
            ServicioEmail servicioEmail) {
        this.repositorioConfigAlerta = repositorioConfigAlerta;
        this.repositorioReserva = repositorioReserva;
        this.servicioClima = servicioClima;
        this.servicioEmail = servicioEmail;
    }
    
    /**
     * Ejecuta cada hora en punto (00 minutos).
     * Cron: "segundo minuto hora día mes día_semana"
     * "0 0 * * * *" = Cada hora en el minuto 0
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void verificarAlertas() {
        log.info("=== Iniciando verificación de alertas climáticas ===");
        
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        LocalTime horaActual = ahora.toLocalTime();
        
        log.debug("Fecha/Hora actual: {}", ahora);
        
        // Obtener todas las configuraciones activas
        List<ConfiguracionAlertaClima> configuraciones = repositorioConfigAlerta.findByActivoTrue();
        
        if (configuraciones.isEmpty()) {
            log.info("No hay configuraciones de alertas activas");
            return;
        }
        
        log.info("Procesando {} configuraciones activas", configuraciones.size());
        
        for (ConfiguracionAlertaClima config : configuraciones) {
            try {
                procesarConfiguracion(config, ahora, hoy, horaActual);
            } catch (Exception e) {
                log.error("Error al procesar configuración del complejo {}: {}", 
                         config.getComplejo().getNombre_complejo(), e.getMessage(), e);
            }
        }
        
        log.info("=== Verificación de alertas climáticas finalizada ===");
    }
    
    /**
     * Procesa una configuración específica buscando reservas que cumplan los criterios.
     */
    private void procesarConfiguracion(ConfiguracionAlertaClima config, 
                                      LocalDateTime ahora, 
                                      LocalDate hoy, 
                                      LocalTime horaActual) {
        
        ComplejoDeportivo complejo = config.getComplejo();
        log.debug("Procesando complejo: {}", complejo.getNombre_complejo());
        
        // Validar que el complejo tenga coordenadas
        if (complejo.getLatitud() == null || complejo.getLongitud() == null) {
            log.warn("Complejo {} no tiene coordenadas configuradas. Saltando alertas.", 
                    complejo.getNombre_complejo());
            return;
        }
        
        List<Reserva> reservasCandidatas;
        
        if (config.getEstrategia() == EstrategiaAlerta.HORAS_ANTES) {
            // Estrategia HORAS_ANTES: buscar reservas X horas en el futuro
            reservasCandidatas = buscarReservasHorasAntes(complejo, ahora, config.getHorasAnticipacion());
            
        } else if (config.getEstrategia() == EstrategiaAlerta.HORARIO_FIJO) {
            // Estrategia HORARIO_FIJO: buscar reservas del día actual en el horario configurado
            reservasCandidatas = buscarReservasHorarioFijo(complejo, hoy, config.getHorarioFijo(), horaActual);
            
        } else {
            log.warn("Estrategia desconocida para complejo {}: {}", 
                    complejo.getNombre_complejo(), config.getEstrategia());
            return;
        }
        
        log.debug("Encontradas {} reservas candidatas para complejo {}", 
                 reservasCandidatas.size(), complejo.getNombre_complejo());
        
        // Procesar cada reserva candidata
        for (Reserva reserva : reservasCandidatas) {
            procesarReserva(reserva, config);
        }
    }
    
    /**
     * Busca reservas para estrategia HORAS_ANTES.
     * Ejemplo: Si son las 10:00 y la configuración es 24 horas antes,
     * busca reservas que sean mañana a las 10:00.
     */
    private List<Reserva> buscarReservasHorasAntes(ComplejoDeportivo complejo, 
                                                   LocalDateTime ahora, 
                                                   Integer horasAnticipacion) {
        
        LocalDateTime fechaObjetivo = ahora.plusHours(horasAnticipacion);
        LocalDate diaObjetivo = fechaObjetivo.toLocalDate();
        LocalTime horaInicio = fechaObjetivo.toLocalTime().minusMinutes(30); // Margen de 30 min
        LocalTime horaFin = fechaObjetivo.toLocalTime().plusMinutes(30);
        
        log.debug("Buscando reservas para {} horas antes: día={}, hora={} a {}", 
                 horasAnticipacion, diaObjetivo, horaInicio, horaFin);
        
        return repositorioReserva.findReservasParaAlerta(
            complejo.getId_complejo(),
            diaObjetivo,
            horaInicio,
            horaFin,
            EstadoReserva.CONFIRMADA,
            false // alertaEnviada = false
        );
    }
    
    /**
     * Busca reservas para estrategia HORARIO_FIJO.
     * Ejemplo: Si la configuración es 06:00 AM y ahora son las 06:00 AM,
     * busca todas las reservas de hoy que aún no tengan alerta.
     */
    private List<Reserva> buscarReservasHorarioFijo(ComplejoDeportivo complejo,
                                                   LocalDate hoy,
                                                   LocalTime horarioFijo,
                                                   LocalTime horaActual) {
        
        // Verificar que sea el horario configurado (con margen de 1 hora)
        if (Math.abs(horaActual.getHour() - horarioFijo.getHour()) > 0) {
            log.debug("No es el horario fijo configurado ({}). Hora actual: {}", 
                     horarioFijo, horaActual);
            return List.of();
        }
        
        log.debug("Es el horario fijo ({}). Buscando reservas de hoy: {}", horarioFijo, hoy);
        
        return repositorioReserva.findReservasPorComplejoFechaEstadoYAlerta(
            complejo.getId_complejo(),
            hoy,
            EstadoReserva.CONFIRMADA,
            false // alertaEnviada = false
        );
    }
    
    /**
     * Procesa una reserva individual: consulta el clima y envía alerta si es necesario.
     */
    private void procesarReserva(Reserva reserva, ConfiguracionAlertaClima config) {
        try {
            log.debug("Procesando reserva ID={}, Cliente={}", 
                     reserva.getId(), reserva.getCliente().getEmail());
            
            // Obtener primer detalle de la reserva (para fecha/hora)
            if (reserva.getDetalles().isEmpty()) {
                log.warn("Reserva {} no tiene detalles. Saltando.", reserva.getId());
                return;
            }
            
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            ComplejoDeportivo complejo = config.getComplejo();
            
            // Construir fecha/hora completa de la reserva
            LocalDateTime fechaHoraReserva = LocalDateTime.of(
                reserva.getFechaReserva(),
                primerDetalle.getHoraInicio()
            );
            
            // Consultar clima (devuelve tipo de precipitación)
            RespuestaClimaDTO clima = servicioClima.consultarClima(
                complejo.getLatitud(),
                complejo.getLongitud(),
                fechaHoraReserva,
                config.getUmbralProbabilidad()
            );

            // Construir umbrales por tipo a partir de la configuración del complejo (fallback al umbral general)
            int fallback = config.getUmbralProbabilidad() != null ? config.getUmbralProbabilidad() : 50;
            java.util.Map<com.example.tureserva.servicio.dto.TipoPrecipitacion, Integer> umbralesPorTipo = new java.util.HashMap<>();
            umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.LLOVIZNA, config.getUmbralLlovizna() != null ? config.getUmbralLlovizna() : fallback);
            umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.LLUVIA, config.getUmbralLluvia() != null ? config.getUmbralLluvia() : fallback);
            umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.CHAPARRON, config.getUmbralChubascos() != null ? config.getUmbralChubascos() : fallback);
            umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.TORMENTA, config.getUmbralTormenta() != null ? config.getUmbralTormenta() : fallback);
            umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.NIEVE, config.getUmbralNieve() != null ? config.getUmbralNieve() : fallback);
            umbralesPorTipo.put(com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO, config.getUmbralOtro() != null ? config.getUmbralOtro() : fallback);

            com.example.tureserva.servicio.dto.TipoPrecipitacion tipo = clima.getTipoPrecipitacion() != null ? clima.getTipoPrecipitacion() : com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO;

            int umbralTipo = umbralesPorTipo.getOrDefault(tipo, fallback);
            Integer prob = clima.getProbabilidadPrecipitacion() != null ? clima.getProbabilidadPrecipitacion() : 0;

            boolean hayMalClimaSegunTipo = prob >= umbralTipo;

            if (hayMalClimaSegunTipo) {
                log.info("¡Mal clima detectado para reserva {}! Tipo={}, probabilidad={}%, umbral={}. Enviando alerta...", reserva.getId(), tipo, prob, umbralTipo);

                servicioEmail.enviarAlertaClimatica(
                    reserva,
                    clima.getDescripcion(),
                    prob,
                    clima.getTipoPrecipitacion(),
                    clima.getPrecipMmHora()
                );

                // Marcar alerta como enviada
                reserva.setAlertaEnviada(true);
                repositorioReserva.save(reserva);

                log.info("Alerta climática enviada exitosamente para reserva {}", reserva.getId());
            } else {
                log.debug("No hay mal clima para reserva {}. Tipo={}, Probabilidad={}%, umbral={}", 
                         reserva.getId(), tipo, prob, umbralTipo);
            }
            
        } catch (Exception e) {
            log.error("Error al procesar reserva {}: {}", reserva.getId(), e.getMessage(), e);
        }
    }
}
