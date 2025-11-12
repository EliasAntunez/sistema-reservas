package com.example.tureserva.servicio;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.ConfiguracionHorario;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.repositorio.RepositorioConfiguracionHorario;
import com.example.tureserva.repositorio.RepositorioEspacioReservable;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Servicio para gestionar ConfiguracionHorario.
 * Maneja la lógica de negocio para crear, actualizar y eliminar configuraciones de horarios.
 * 
 * <p>Implementa eliminación lógica (soft delete) para mantener historial de auditoría.
 * Las configuraciones eliminadas mantienen su relación con el complejo pero están marcadas como inactivas.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ServicioConfiguracionHorario {
    
    private final RepositorioConfiguracionHorario repositorioConfiguracionHorario;
    private final RepositorioEspacioReservable repositorioEspacioReservable;
    private final RepositorioComplejoDeportivo repositorioComplejoDeportivo;
    
    /**
     * Lista todas las configuraciones ACTIVAS de horario de un complejo
     */
    public List<ConfiguracionHorario> listarPorComplejo(ComplejoDeportivo complejo) {
        return repositorioConfiguracionHorario.findByComplejoDeportivoAndActivoTrue(complejo);
    }
    
    /**
     * Lista todas las configuraciones ACTIVAS de un complejo con sus rangos cargados
     */
    public List<ConfiguracionHorario> listarPorComplejoConRangos(ComplejoDeportivo complejo) {
        List<ConfiguracionHorario> configs = repositorioConfiguracionHorario.findByComplejoDeportivoAndActivoTrueWithRangos(complejo);
        // Eliminar duplicados por ID (debido a LEFT JOIN FETCH que puede causar duplicados)
        LinkedHashSet<Long> idsVistos = new LinkedHashSet<>();
        List<ConfiguracionHorario> resultado = new ArrayList<>();
        
        for (ConfiguracionHorario config : configs) {
            if (!idsVistos.contains(config.getId())) {
                idsVistos.add(config.getId());
                resultado.add(config);
            }
        }
        
        return resultado;
    }
    
    /**
     * Lista TODAS las configuraciones (incluidas las eliminadas) con sus rangos
     * Útil para auditoría y reportes
     */
    public List<ConfiguracionHorario> listarTodasPorComplejoConRangos(ComplejoDeportivo complejo) {
        List<ConfiguracionHorario> configs = repositorioConfiguracionHorario.findByComplejoDeportivoWithRangos(complejo);
        LinkedHashSet<Long> idsVistos = new LinkedHashSet<>();
        List<ConfiguracionHorario> resultado = new ArrayList<>();
        
        for (ConfiguracionHorario config : configs) {
            if (!idsVistos.contains(config.getId())) {
                idsVistos.add(config.getId());
                resultado.add(config);
            }
        }
        
        return resultado;
    }
    
    /**
     * Busca una configuración por ID
     */
    public Optional<ConfiguracionHorario> obtenerPorId(Long id) {
        return repositorioConfiguracionHorario.findById(id);
    }
    
    /**
     * Busca una configuración por ID con sus rangos cargados
     */
    public Optional<ConfiguracionHorario> obtenerPorIdConRangos(Long id) {
        return repositorioConfiguracionHorario.findByIdWithRangos(id);
    }
    
    /**
     * Busca una configuración ACTIVA por nombre y complejo
     */
    public Optional<ConfiguracionHorario> obtenerPorNombreYComplejo(String nombre, ComplejoDeportivo complejo) {
        return repositorioConfiguracionHorario.findByNombreAndComplejoDeportivoAndActivoTrue(nombre, complejo);
    }
    
    /**
     * Verifica si existe una configuración ACTIVA con ese nombre en ese complejo
     */
    public boolean existePorNombreYComplejo(String nombre, ComplejoDeportivo complejo) {
        return repositorioConfiguracionHorario.existsByNombreAndComplejoDeportivoAndActivoTrue(nombre, complejo);
    }
    
    /**
     * Crea una nueva configuración de horario
     * 
     * @throws IllegalArgumentException si ya existe una configuración con ese nombre
     */
    @Transactional
    public ConfiguracionHorario crear(ConfiguracionHorario configuracion) {
        // Validar que no exista otra con el mismo nombre en el mismo complejo
        if (existePorNombreYComplejo(configuracion.getNombre(), configuracion.getComplejoDeportivo())) {
            throw new IllegalArgumentException(
                "Ya existe una configuración de horario con el nombre '" + configuracion.getNombre() + 
                "' en este complejo");
        }
        
        return repositorioConfiguracionHorario.save(configuracion);
    }
    
    /**
     * Actualiza una configuración existente
     * 
     * @param configuracion La configuración con los datos actualizados
     * @return La configuración actualizada
     * @throws IllegalArgumentException si el nombre ya existe en otro registro del mismo complejo
     */
    @Transactional
    public ConfiguracionHorario actualizar(ConfiguracionHorario configuracion) {
        log.debug("Actualizando configuración - ID: {}, Nombre: '{}'", configuracion.getId(), configuracion.getNombre());
        
        // Validar que el nombre no esté en uso por otra configuración del mismo complejo
        Optional<ConfiguracionHorario> existente = obtenerPorNombreYComplejo(
            configuracion.getNombre(), 
            configuracion.getComplejoDeportivo()
        );
        
        if (existente.isPresent() && !existente.get().getId().equals(configuracion.getId())) {
            String mensaje = "Ya existe otra configuración de horario con el nombre '" + configuracion.getNombre() + 
                           "' en este complejo";
            log.warn(mensaje);
            throw new IllegalArgumentException(mensaje);
        }
        
        ConfiguracionHorario actualizada = repositorioConfiguracionHorario.save(configuracion);
        log.info("Configuración actualizada exitosamente - ID: {}", actualizada.getId());
        return actualizada;
    }
    
    /**
     * Elimina lógicamente una configuración de horario (soft delete)
     * 
     * @param id ID de la configuración a eliminar
     * @param motivo Motivo de la eliminación (para auditoría)
     * @param desvincularAutomaticamente Si es true, desvincula automáticamente la configuración de todos los lugares donde esté asignada
     * @throws IllegalArgumentException si la configuración no existe
     * @throws IllegalStateException si está en uso y no se eligió desvincular automáticamente, o si ya fue eliminada
     */
    @Transactional
    public void eliminarLogicamente(Long id, String motivo, boolean desvincularAutomaticamente) {
        log.debug("Iniciando eliminación lógica - ID: {}, Motivo: '{}', Desvincular: {}", id, motivo, desvincularAutomaticamente);
        
        ConfiguracionHorario config = obtenerPorIdConRangos(id)
            .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada con ID: " + id));
        
        if (!config.estaActiva()) {
            log.warn("Intento de eliminar configuración ya eliminada - ID: {}", id);
            throw new IllegalStateException("La configuración ya fue eliminada anteriormente");
        }
        
        // Verificar si está en uso
        List<String> usos = obtenerUsos(config);
        
        if (!usos.isEmpty()) {
            log.info("Configuración en uso - ID: {}, Usos: {}", id, usos.size());
            if (!desvincularAutomaticamente) {
                String mensaje = "La configuración está en uso. Usos encontrados: " + String.join(", ", usos) + 
                               ". Debes desvincularla o permitir desvinculación automática.";
                log.warn(mensaje);
                throw new IllegalStateException(mensaje);
            }
            
            // Desvincular automáticamente
            log.info("Desvinculando automáticamente configuración - ID: {}", id);
            desvincularConfiguracion(config);
        }
        
        // Marcar como eliminada
        config.eliminarLogicamente(motivo);
        repositorioConfiguracionHorario.save(config);
        log.info("Configuración eliminada lógicamente - ID: {}, Nombre: '{}'", id, config.getNombre());
    }
    
    /**
     * Restaura una configuración eliminada lógicamente
     */
    @Transactional
    public void restaurar(Long id) {
        ConfiguracionHorario config = obtenerPorId(id)
            .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada"));
        
        if (config.estaActiva()) {
            throw new IllegalStateException("La configuración no está eliminada");
        }
        
        // Verificar que no exista otra activa con el mismo nombre
        if (existePorNombreYComplejo(config.getNombre(), config.getComplejoDeportivo())) {
            throw new IllegalStateException(
                "Ya existe una configuración activa con el nombre '" + config.getNombre() + "' en este complejo");
        }
        
        config.restaurar();
        repositorioConfiguracionHorario.save(config);
    }
    
    /**
     * Obtiene una lista de lugares donde se usa esta configuración
     */
    public List<String> obtenerUsos(ConfiguracionHorario config) {
        List<String> usos = new ArrayList<>();
        
        // Buscar el complejo al que pertenece
        ComplejoDeportivo complejo = config.getComplejoDeportivo();
        
        // Verificar si es horario por defecto
        if (complejo.getConfiguracionHorarioMaster() != null && 
            complejo.getConfiguracionHorarioMaster().getId().equals(config.getId())) {
            usos.add("Horario Por Defecto del complejo");
        }
        
        // Verificar si es override de canchas
        if (complejo.getConfiguracionHorarioCanchas() != null && 
            complejo.getConfiguracionHorarioCanchas().getId().equals(config.getId())) {
            usos.add("Horario Override para Canchas");
        }
        
        // Verificar si es override de salones
        if (complejo.getConfiguracionHorarioSalones() != null && 
            complejo.getConfiguracionHorarioSalones().getId().equals(config.getId())) {
            usos.add("Horario Override para Salones");
        }
        
        // Verificar espacios con horario personalizado
        List<EspacioReservable> espaciosConEstaConfig = repositorioEspacioReservable
            .findByHorarioPersonalizado(config);
        
        if (!espaciosConEstaConfig.isEmpty()) {
            usos.add(espaciosConEstaConfig.size() + " espacio(s) con horario personalizado");
        }
        
        return usos;
    }
    
    /**
     * Desvincula una configuración de todos los lugares donde está asignada
     * <p>Esto incluye: horario master, override de canchas, override de salones, y horarios personalizados de espacios.
     * 
     * @param config La configuración a desvincular
     */
    @Transactional
    public void desvincularConfiguracion(ConfiguracionHorario config) {
        log.debug("Desvinculando configuración - ID: {}, Nombre: '{}'", config.getId(), config.getNombre());
        
        ComplejoDeportivo complejo = config.getComplejoDeportivo();
        int desvinculaciones = 0;
        
        // Remover de master
        if (complejo.getConfiguracionHorarioMaster() != null && 
            complejo.getConfiguracionHorarioMaster().getId().equals(config.getId())) {
            complejo.setConfiguracionHorarioMaster(null);
            repositorioComplejoDeportivo.save(complejo);
            desvinculaciones++;
            log.debug("Desvinculado de Master");
        }
        
        // Remover de override canchas
        if (complejo.getConfiguracionHorarioCanchas() != null && 
            complejo.getConfiguracionHorarioCanchas().getId().equals(config.getId())) {
            complejo.setConfiguracionHorarioCanchas(null);
            repositorioComplejoDeportivo.save(complejo);
            desvinculaciones++;
            log.debug("Desvinculado de Override Canchas");
        }
        
        // Remover de override salones
        if (complejo.getConfiguracionHorarioSalones() != null && 
            complejo.getConfiguracionHorarioSalones().getId().equals(config.getId())) {
            complejo.setConfiguracionHorarioSalones(null);
            repositorioComplejoDeportivo.save(complejo);
            desvinculaciones++;
            log.debug("Desvinculado de Override Salones");
        }
        
        // Remover de espacios personalizados
        List<EspacioReservable> espaciosConEstaConfig = repositorioEspacioReservable
            .findByHorarioPersonalizado(config);
        
        for (EspacioReservable espacio : espaciosConEstaConfig) {
            espacio.setHorarioPersonalizado(null);
            repositorioEspacioReservable.save(espacio);
            desvinculaciones++;
        }
        
        log.info("Configuración desvinculada exitosamente - ID: {}, Total desvinculaciones: {}", config.getId(), desvinculaciones);
    }
    
    /**
     * Verifica si una configuración puede ser eliminada (no está en uso)
     */
    public boolean puedeEliminar(ConfiguracionHorario configuracion) {
        return obtenerUsos(configuracion).isEmpty();
    }
}
