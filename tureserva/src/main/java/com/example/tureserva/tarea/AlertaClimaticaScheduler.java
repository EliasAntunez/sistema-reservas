package com.example.tureserva.tarea;

import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.repositorio.RepositorioConfiguracionAlertaClima;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.ServicioAlertaClimatica;
import com.example.tureserva.servicio.clima.AlertaClimaConstants;
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
    private final ServicioAlertaClimatica servicioAlerta;
    
    public AlertaClimaticaScheduler(
            RepositorioConfiguracionAlertaClima repositorioConfigAlerta,
            RepositorioReserva repositorioReserva,
            ServicioAlertaClimatica servicioAlerta) {
        this.repositorioConfigAlerta = repositorioConfigAlerta;
        this.repositorioReserva = repositorioReserva;
        this.servicioAlerta = servicioAlerta;
    }
    
    /**
     * Ejecuta cada hora en punto (00 minutos).
     * Cron: "segundo minuto hora día mes día_semana"
     * "0 0 * * * *" = Cada hora en el minuto 0
     */
    @Scheduled(cron = "0 0 * * * *")
    public void verificarAlertas() {
        log.info("🌦️ Iniciando verificación horaria de alertas climáticas");
        
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
        
        int totalProcesadas = 0;
        int totalAlertas = 0;
        
        for (ConfiguracionAlertaClima config : configuraciones) {
            try {
                int[] resultado = procesarConfiguracion(config, ahora, hoy, horaActual);
                totalProcesadas += resultado[0];
                totalAlertas += resultado[1];
            } catch (Exception e) {
                log.error("Error al procesar configuración del complejo {}: {}", 
                         config.getComplejo().getNombre_complejo(), e.getMessage(), e);
            }
        }
        
        log.info("✅ Verificación completada: {} configs, {} reservas procesadas, {} alertas enviadas",
                configuraciones.size(), totalProcesadas, totalAlertas);
    }
    
    /**
     * Procesa una configuración específica buscando reservas que cumplan los criterios.
     * @return Array [reservasProcesadas, alertasEnviadas]
     */
    @Transactional(readOnly = true)
    protected int[] procesarConfiguracion(ConfiguracionAlertaClima config, 
                                      LocalDateTime ahora, 
                                      LocalDate hoy, 
                                      LocalTime horaActual) {
        
        ComplejoDeportivo complejo = config.getComplejo();
        log.debug("Procesando complejo: {}", complejo.getNombre_complejo());
        
        // Validar que el complejo tenga coordenadas
        if (complejo.getLatitud() == null || complejo.getLongitud() == null) {
            log.warn("Complejo {} no tiene coordenadas configuradas. Saltando alertas.", 
                    complejo.getNombre_complejo());
            return new int[]{0, 0};
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
            return new int[]{0, 0};
        }
        
        log.debug("Encontradas {} reservas candidatas para complejo {}", 
                 reservasCandidatas.size(), complejo.getNombre_complejo());
        
        int procesadas = 0;
        int enviadas = 0;
        
        // Procesar cada reserva candidata
        for (Reserva reserva : reservasCandidatas) {
            try {
                boolean alertaEnviada = servicioAlerta.verificarYEnviarAlerta(reserva, config);
                procesadas++;
                if (alertaEnviada) {
                    enviadas++;
                    // Marcar alerta como enviada
                    reserva.setAlertaEnviada(true);
                    repositorioReserva.save(reserva);
                }
            } catch (Exception e) {
                log.error("Error procesando reserva {}: {}", reserva.getId(), e.getMessage());
            }
        }
        
        return new int[]{procesadas, enviadas};
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
        LocalTime horaInicio = fechaObjetivo.toLocalTime().minusMinutes(AlertaClimaConstants.VENTANA_MINUTOS_ANTES);
        LocalTime horaFin = fechaObjetivo.toLocalTime().plusMinutes(AlertaClimaConstants.VENTANA_MINUTOS_DESPUES);
        
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
    
}
