package com.example.tureserva.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tureserva.modelo.HorarioComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.DiaSemana;
import com.example.tureserva.repositorio.RepositorioHorarioComplejo;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ServicioHorarioComplejo {
    
    private final RepositorioHorarioComplejo repositorioHorarioComplejo;

    public ServicioHorarioComplejo(RepositorioHorarioComplejo repositorioHorarioComplejo) {
        this.repositorioHorarioComplejo = repositorioHorarioComplejo;
    }

    /**
     * Obtener todos los horarios de un complejo
     */
    public List<HorarioComplejo> obtenerHorariosPorComplejo(ComplejoDeportivo complejo) {
        return repositorioHorarioComplejo.findByComplejoDeportivoOrderByDiaSemana(complejo);
    }

    /**
     * Obtener TODOS los horarios de un complejo para un día específico
     */
    public List<HorarioComplejo> obtenerTodosHorariosPorComplejoYDia(ComplejoDeportivo complejo, DiaSemana diaSemana) {
        return repositorioHorarioComplejo.findAllByComplejoDeportivoAndDiaSemana(complejo, diaSemana);
    }

    /**
     * Obtener horario específico por complejo y día (primer resultado)
     */
    public Optional<HorarioComplejo> obtenerHorarioPorComplejoYDia(ComplejoDeportivo complejo, DiaSemana diaSemana) {
        return repositorioHorarioComplejo.findByComplejoDeportivoAndDiaSemana(complejo, diaSemana);
    }

    /**
     * Obtener horario por ID
     */
    public Optional<HorarioComplejo> obtenerPorId(Long id) {
        return repositorioHorarioComplejo.findById(id);
    }

    /**
     * Guardar horario con validación de solapamiento
     */
    public HorarioComplejo guardarHorario(HorarioComplejo horario) {
        // Validar datos básicos
        String error = validarHorarioBasico(horario);
        if (error != null) {
            throw new RuntimeException(error);
        }
        
        // Validar solapamiento con otros horarios del mismo día
        if (tieneSolapamiento(horario)) {
            throw new RuntimeException("El horario se solapa con otro horario existente del mismo día");
        }
        
        return repositorioHorarioComplejo.save(horario);
    }

    /**
     * Actualizar horario con validación de solapamiento
     */
    public HorarioComplejo actualizarHorario(HorarioComplejo horario) {
        // Validar datos básicos
        String error = validarHorarioBasico(horario);
        if (error != null) {
            throw new RuntimeException(error);
        }
        
        // Validar solapamiento con otros horarios del mismo día (excluyendo el mismo)
        if (tieneSolapamientoExcluyendo(horario, horario.getId())) {
            throw new RuntimeException("El horario se solapa con otro horario existente del mismo día");
        }
        
        return repositorioHorarioComplejo.save(horario);
    }

    /**
     * Eliminar horario por ID
     */
    public void eliminarHorario(Long horarioId) {
        repositorioHorarioComplejo.deleteById(horarioId);
    }

    /**
     * Verificar si existe AL MENOS UN horario para un día específico
     */
    public boolean existeHorarioParaDia(ComplejoDeportivo complejo, DiaSemana dia) {
        return repositorioHorarioComplejo.existsByComplejoDeportivoAndDiaSemana(complejo, dia);
    }

    /**
     * Validar horario básico (sin validar solapamiento)
     */
    public boolean validarHorario(HorarioComplejo horario) {
        return validarHorarioBasico(horario) == null;
    }

    /**
     * Verificar si un complejo está abierto en un día y hora específicos
     * Considera TODOS los horarios del día
     */
    public boolean estaAbierto(ComplejoDeportivo complejo, DiaSemana dia, LocalTime hora) {
        List<HorarioComplejo> horariosDelDia = obtenerTodosHorariosPorComplejoYDia(complejo, dia);
        
        for (HorarioComplejo horario : horariosDelDia) {
            if (estaEnRango(hora, horario.getHoraApertura(), horario.getHoraCierre())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Contar horarios configurados para un complejo
     */
    public long contarHorariosConfigurados(ComplejoDeportivo complejo) {
        return repositorioHorarioComplejo.countByComplejoDeportivo(complejo);
    }

    /**
     * Crear horarios por defecto para un complejo
     */
    public void crearHorariosDefecto(ComplejoDeportivo complejo) {
        // Lunes a Viernes: 16:00 - 23:59
        LocalTime aperturaLV = LocalTime.of(16, 0);
        LocalTime cierreLV = LocalTime.of(23, 59);

        // Sábados y Domingos: 15:00 - 23:59
        LocalTime aperturaSD = LocalTime.of(15, 0);
        LocalTime cierreSD = LocalTime.of(23, 59);

        for (DiaSemana dia : DiaSemana.values()) {
            // Solo crear si no existe ya un horario para ese día
            if (!existeHorarioParaDia(complejo, dia)) {
                HorarioComplejo horario = new HorarioComplejo();
                horario.setComplejoDeportivo(complejo);
                horario.setDiaSemana(dia);
                
                if (dia == DiaSemana.SABADO || dia == DiaSemana.DOMINGO) {
                    horario.setHoraApertura(aperturaSD);
                    horario.setHoraCierre(cierreSD);
                } else {
                    horario.setHoraApertura(aperturaLV);
                    horario.setHoraCierre(cierreLV);
                }
                
                repositorioHorarioComplejo.save(horario); // Usar save directo para evitar validación en horarios por defecto
            }
        }
    }

    // ===== MÉTODOS PRIVADOS DE VALIDACIÓN =====

    /**
     * Validar datos básicos del horario
     */
    private String validarHorarioBasico(HorarioComplejo horario) {
        if (horario == null) return "Horario no válido";
        if (horario.getDiaSemana() == null) return "Debe seleccionar un día de la semana";
        if (horario.getHoraApertura() == null) return "Debe especificar hora de apertura";
        if (horario.getHoraCierre() == null) return "Debe especificar hora de cierre";
        if (!horario.getHoraCierre().isAfter(horario.getHoraApertura())) {
            return "La hora de cierre debe ser posterior a la hora de apertura";
        }
        return null; // Sin errores
    }

    /**
     * Verificar si un horario tiene solapamiento con otros del mismo día
     */
    private boolean tieneSolapamiento(HorarioComplejo nuevoHorario) {
        List<HorarioComplejo> horariosExistentes = obtenerTodosHorariosPorComplejoYDia(
            nuevoHorario.getComplejoDeportivo(), 
            nuevoHorario.getDiaSemana()
        );

        for (HorarioComplejo existente : horariosExistentes) {
            if (seSuperponen(nuevoHorario, existente)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Verificar si un horario tiene solapamiento con otros del mismo día (excluyendo uno específico)
     */
    private boolean tieneSolapamientoExcluyendo(HorarioComplejo horario, Long idExcluir) {
        List<HorarioComplejo> horariosExistentes = obtenerTodosHorariosPorComplejoYDia(
            horario.getComplejoDeportivo(), 
            horario.getDiaSemana()
        );

        for (HorarioComplejo existente : horariosExistentes) {
            // Excluir el horario que se está editando
            if (idExcluir != null && existente.getId().equals(idExcluir)) {
                continue;
            }
            
            if (seSuperponen(horario, existente)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Verificar si dos horarios se superponen
     */
    private boolean seSuperponen(HorarioComplejo h1, HorarioComplejo h2) {
        // h1: inicio1 - fin1
        // h2: inicio2 - fin2
        // Se superponen si: inicio1 < fin2 AND inicio2 < fin1
        return h1.getHoraApertura().isBefore(h2.getHoraCierre()) && 
               h2.getHoraApertura().isBefore(h1.getHoraCierre());
    }

    /**
     * Verificar si una hora está dentro de un rango
     */
    private boolean estaEnRango(LocalTime hora, LocalTime inicio, LocalTime fin) {
        return !hora.isBefore(inicio) && hora.isBefore(fin);
    }
}