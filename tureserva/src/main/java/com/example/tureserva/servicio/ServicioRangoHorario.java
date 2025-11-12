package com.example.tureserva.servicio;

import com.example.tureserva.modelo.ConfiguracionHorario;
import com.example.tureserva.modelo.RangoHorario;
import com.example.tureserva.repositorio.RepositorioRangoHorario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/**
 * Servicio para gestionar RangoHorario.
 * Incluye validación de solapamientos antes de crear/actualizar.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioRangoHorario {
    
    private final RepositorioRangoHorario repositorioRangoHorario;
    
    /**
     * Lista todos los rangos de una configuración
     */
    public List<RangoHorario> listarPorConfiguracion(ConfiguracionHorario configuracion) {
        return repositorioRangoHorario.findByConfiguracionHorario(configuracion);
    }
    
    /**
     * Lista rangos de una configuración para un día específico
     */
    public List<RangoHorario> listarPorConfiguracionYDia(
            ConfiguracionHorario configuracion, 
            DayOfWeek dia) {
        return repositorioRangoHorario.findByConfiguracionHorarioAndDiaSemana(configuracion, dia);
    }
    
    /**
     * Busca un rango por ID
     */
    public Optional<RangoHorario> obtenerPorId(Long id) {
        return repositorioRangoHorario.findById(id);
    }
    
    /**
     * Crea un nuevo rango horario con validación de solapamiento
     * 
     * @throws IllegalArgumentException si hay solapamiento con rangos existentes
     */
    @Transactional
    public RangoHorario crear(RangoHorario rango) {
        validarSolapamiento(rango, null);
        return repositorioRangoHorario.save(rango);
    }
    
    /**
     * Actualiza un rango existente con validación de solapamiento
     * 
     * @throws IllegalArgumentException si hay solapamiento con otros rangos
     */
    @Transactional
    public RangoHorario actualizar(RangoHorario rango) {
        validarSolapamiento(rango, rango.getId());
        return repositorioRangoHorario.save(rango);
    }
    
    /**
     * Elimina un rango horario
     */
    @Transactional
    public void eliminar(Long id) {
        RangoHorario rango = obtenerPorId(id)
            .orElseThrow(() -> new IllegalArgumentException("Rango no encontrado"));
        
        repositorioRangoHorario.delete(rango);
    }
    
    /**
     * Valida que el rango no se solape con otros rangos del mismo día
     * 
     * @param rango El rango a validar
     * @param idExcluir ID del rango a excluir de la validación (para UPDATE)
     * @throws IllegalArgumentException si hay solapamiento
     */
    private void validarSolapamiento(RangoHorario rango, Long idExcluir) {
        boolean haySolapamiento = repositorioRangoHorario.existeSolapamiento(
            rango.getConfiguracionHorario(),
            rango.getDiaSemana(),
            rango.getHoraApertura(),
            rango.getHoraCierre(),
            idExcluir
        );
        
        if (haySolapamiento) {
            String mensaje = String.format(
                "El horario %s - %s para %s se solapa con otro rango existente",
                rango.getHoraApertura(),
                rango.getHoraCierre(),
                getDiaNombre(rango.getDiaSemana())
            );
            throw new IllegalArgumentException(mensaje);
        }
    }
    
    /**
     * Obtiene el nombre del día en español
     */
    private String getDiaNombre(DayOfWeek dia) {
        return switch(dia) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}
