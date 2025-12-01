package com.example.tureserva.controlador;

import com.example.tureserva.modelo.DetalleReserva;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.ServicioReserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador para manejar las acciones de las alertas climáticas.
 * Procesa los clicks en los botones del email de alerta.
 */
@Controller
@RequestMapping("/alerta-clima")
public class ControladorAlertaClima {
    
    private static final Logger log = LoggerFactory.getLogger(ControladorAlertaClima.class);
    
    private final RepositorioReserva repositorioReserva;
    private final ServicioReserva servicioReserva;
    
    public ControladorAlertaClima(RepositorioReserva repositorioReserva,
                                 ServicioReserva servicioReserva) {
        this.repositorioReserva = repositorioReserva;
        this.servicioReserva = servicioReserva;
    }
    
    /**
     * El cliente decide mantener su reserva a pesar del mal clima.
     * Muestra página de confirmación.
     */
    @GetMapping("/mantener/{reservaId}")
    public String mostrarMantener(@PathVariable Long reservaId, Model model) {
        try {
            Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
            
            if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
                model.addAttribute("error", "Esta reserva no se puede mantener en su estado actual");
                return "alerta-clima/error";
            }
            
            model.addAttribute("reserva", reserva);
            return "alerta-clima/mantener";
            
        } catch (Exception e) {
            log.error("Error al mostrar mantener reserva {}: {}", reservaId, e.getMessage(), e);
            model.addAttribute("error", "Reserva no encontrada");
            return "alerta-clima/error";
        }
    }
    
    /**
     * Procesa la confirmación de mantener la reserva.
     */
    @PostMapping("/mantener/{reservaId}")
    public String confirmarMantener(@PathVariable Long reservaId, Model model) {
        try {
            Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
            
            if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
                model.addAttribute("error", "Esta reserva no se puede mantener");
                return "alerta-clima/error";
            }
            
            log.info("Cliente {} confirmó mantener reserva {} a pesar del mal clima", 
                    reserva.getCliente().getEmail(), reserva.getCodigoReserva());
            
            model.addAttribute("reserva", reserva);
            model.addAttribute("mensaje", "Tu reserva se mantiene confirmada");
            return "alerta-clima/exito";
            
        } catch (Exception e) {
            log.error("Error al confirmar mantener reserva {}: {}", reservaId, e.getMessage(), e);
            model.addAttribute("error", "Ocurrió un error al procesar tu solicitud");
            return "alerta-clima/error";
        }
    }
    
    /**
     * El cliente decide reprogramar su reserva.
     * Muestra calendario y horarios disponibles.
     */
    @GetMapping("/reprogramar/{reservaId}")
    public String mostrarReprogramar(@PathVariable Long reservaId, Model model) {
        try {
            Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

            // Bloquear si la reserva está FINALIZADA
            if (reserva.getEstado() == EstadoReserva.FINALIZADA) {
                model.addAttribute("error", "No puedes reprogramar una reserva que ya está finalizada.");
                return "alerta-clima/error";
            }

            // Bloquear si el horario ya pasó
            if (!reserva.getDetalles().isEmpty()) {
                DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                LocalDateTime fechaHoraReserva = LocalDateTime.of(
                    primerDetalle.getFechaReserva(),
                    primerDetalle.getHoraInicio()
                );
                LocalDateTime ahora = LocalDateTime.now();
                if (fechaHoraReserva.isBefore(ahora) || fechaHoraReserva.equals(ahora)) {
                    model.addAttribute("error", "No puedes reprogramar una reserva cuyo horario ya pasó.");
                    return "alerta-clima/error";
                }
            }

            if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
                model.addAttribute("error", "Esta reserva no se puede reprogramar en su estado actual");
                return "alerta-clima/error";
            }

            // Validar política de cancelación para reprogramación
            ServicioReserva.ResultadoValidacionReprogramacion validacion = servicioReserva.validarReprogramacion(reserva);
            if (!validacion.isCumplePolitica()) {
                model.addAttribute("error", validacion.getMensaje());
                return "alerta-clima/error";
            }
            // ...existing code...
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            EspacioReservable espacio = primerDetalle.getEspacioReservable();
            model.addAttribute("reserva", reserva);
            model.addAttribute("espacio", espacio);
            model.addAttribute("fechaActual", reserva.getFechaReserva());
            model.addAttribute("horaActual", primerDetalle.getHoraInicio());
            model.addAttribute("validacion", validacion);
            return "alerta-clima/reprogramar";

        } catch (Exception e) {
            log.error("Error al mostrar reprogramar reserva {}: {}", reservaId, e.getMessage(), e);
            model.addAttribute("error", "Reserva no encontrada");
            return "alerta-clima/error";
        }
    }
    
    /**
     * Obtiene horarios disponibles para una fecha específica.
     */
    @GetMapping("/reprogramar/{reservaId}/horarios")
    @ResponseBody
    public List<String> obtenerHorariosDisponibles(
            @PathVariable Long reservaId,
            @RequestParam String fecha) {
        
        try {
            Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
            
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            EspacioReservable espacio = primerDetalle.getEspacioReservable();
            LocalDate fechaSolicitada = LocalDate.parse(fecha);
            
            // Obtener horarios disponibles usando el servicio
            List<LocalTime> horariosDisponibles = servicioReserva
                .obtenerHorariosDisponibles(espacio.getId(), fechaSolicitada);
            
            return horariosDisponibles.stream()
                .map(LocalTime::toString)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error al obtener horarios: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Procesa la reprogramación de la reserva.
     * Marca la reserva original como REPROGRAMADA y crea una nueva reserva CONFIRMADA.
     */
    @PostMapping("/reprogramar/{reservaId}")
    public String procesarReprogramacion(
            @PathVariable Long reservaId,
            @RequestParam String nuevaFecha,
            @RequestParam String nuevaHora,
            Model model) {
        
        try {
            Reserva reservaOriginal = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
            if (reservaOriginal.getEstado() != EstadoReserva.CONFIRMADA) {
                model.addAttribute("error", "Esta reserva no se puede reprogramar");
                return "alerta-clima/error";
            }
            ServicioReserva.ResultadoValidacionReprogramacion validacion = servicioReserva.validarReprogramacion(reservaOriginal);
            if (!validacion.isCumplePolitica()) {
                model.addAttribute("error", validacion.getMensaje());
                return "alerta-clima/error";
            }
            LocalDate fecha = LocalDate.parse(nuevaFecha);
            LocalTime hora = LocalTime.parse(nuevaHora);
            LocalDate fechaOriginal = reservaOriginal.getFechaReserva();
            LocalTime horaOriginal = reservaOriginal.getDetalles().get(0).getHoraInicio();
            reservaOriginal.setEstado(EstadoReserva.REPROGRAMADA);
            repositorioReserva.save(reservaOriginal);
            Reserva nuevaReserva = servicioReserva.crearReservaPorReprogramacion(
                reservaOriginal, fecha, hora);
            log.info("Reserva {} reprogramada: original {} {} → nueva {} {} {} por alerta climática", 
                    reservaOriginal.getCodigoReserva(), 
                    fechaOriginal, horaOriginal,
                    nuevaReserva.getCodigoReserva(), fecha, hora);
            model.addAttribute("reserva", nuevaReserva);
            model.addAttribute("reservaOriginal", reservaOriginal);
            model.addAttribute("mensaje", "Tu reserva ha sido reprogramada exitosamente");
            model.addAttribute("nuevaFecha", fecha);
            model.addAttribute("nuevaHora", hora);
            return "alerta-clima/exito";
        } catch (Exception e) {
            log.error("Error al reprogramar reserva {}: {}", reservaId, e.getMessage(), e);
            model.addAttribute("error", "Ocurrió un error al reprogramar tu reserva: " + e.getMessage());
            return "alerta-clima/error";
        }
    }
    
    /**
     * El cliente decide cancelar su reserva debido al mal clima.
     * Muestra página de confirmación.
     * Verifica si hay seña y política de cancelación.
     */
    @GetMapping("/cancelar/{reservaId}")
    public String mostrarCancelar(@PathVariable Long reservaId, Model model) {
        try {
            Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
            
            if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
                model.addAttribute("error", "Esta reserva no se puede cancelar en su estado actual");
                return "alerta-clima/error";
            }
            
            // Verificar si el espacio requiere seña
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            EspacioReservable espacio = primerDetalle.getEspacioReservable();
            boolean requiereSenia = espacio.getPoliticaSenia() != null;
            
            boolean cumplePolitica = true;
            String mensajePolitica = null;
            
            if (requiereSenia) {
                // Validar política de cancelación (similar a la de reprogramación)
                ServicioReserva.ResultadoValidacionReprogramacion validacion = 
                    servicioReserva.validarReprogramacion(reserva);
                cumplePolitica = validacion.isCumplePolitica();
                mensajePolitica = validacion.getMensaje();
            }
            
            model.addAttribute("reserva", reserva);
            model.addAttribute("requiereSenia", requiereSenia);
            model.addAttribute("cumplePolitica", cumplePolitica);
            model.addAttribute("mensajePolitica", mensajePolitica);
            
            return "alerta-clima/cancelar";
            
        } catch (Exception e) {
            log.error("Error al mostrar cancelar reserva {}: {}", reservaId, e.getMessage(), e);
            model.addAttribute("error", "Reserva no encontrada");
            return "alerta-clima/error";
        }
    }
    
    /**
     * Procesa la cancelación de la reserva.
     */
    @PostMapping("/cancelar/{reservaId}")
    public String procesarCancelacion(@PathVariable Long reservaId, Model model) {
        try {
            Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
            
            if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
                model.addAttribute("error", "Esta reserva no se puede cancelar");
                return "alerta-clima/error";
            }
            
            reserva.setEstado(EstadoReserva.CANCELADA);
            repositorioReserva.save(reserva);
            
            log.info("Reserva {} cancelada por alerta climática", reserva.getCodigoReserva());
            
            model.addAttribute("reserva", reserva);
            model.addAttribute("mensaje", "Tu reserva ha sido cancelada");
            return "alerta-clima/exito";
            
        } catch (Exception e) {
            log.error("Error al cancelar reserva {}: {}", reservaId, e.getMessage(), e);
            model.addAttribute("error", "Ocurrió un error al cancelar tu reserva");
            return "alerta-clima/error";
        }
    }
}
