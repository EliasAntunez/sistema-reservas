package com.example.tureserva.servicio;

import org.springframework.stereotype.Service;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Salon;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.repositorio.RepositorioSalon;
import java.util.Optional;


@Service
public class ServicioSalon {

    private final RepositorioSalon repositorioSalon;
    private final RepositorioComplejoDeportivo repositorioComplejo;
    private final ServicioNormalizacion servicioNormalizacion;

    public ServicioSalon(RepositorioSalon repositorioSalon, 
                        RepositorioComplejoDeportivo repositorioComplejo,
                        ServicioNormalizacion servicioNormalizacion) {
        this.repositorioSalon = repositorioSalon;
        this.repositorioComplejo = repositorioComplejo;
        this.servicioNormalizacion = servicioNormalizacion;
    }

    public org.springframework.data.domain.Page<Salon> listarSalonesPorComplejoPaginado(Long idComplejo, int page, int size) {
        ComplejoDeportivo complejo = repositorioComplejo.findById(idComplejo).orElse(null);
        if (complejo == null) return org.springframework.data.domain.Page.empty();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size);
        return repositorioSalon.findByComplejoDeportivoAndActivoTrue(complejo, pageable);
    }

    // ELIMINADO: Método de normalización movido a ServicioNormalizacion
    // - normalizarNombre() → servicioNormalizacion.normalizarNombreApellido()

    public Optional<Salon> obtenerPorId(Long id) {
        return repositorioSalon.findById(id);
    }

    /**
     * Verifica si ya existe un salón activo con el mismo nombre en el complejo
     * @param nombre Nombre a verificar
     * @param complejoDeportivo Complejo deportivo
     * @return true si existe, false si no existe
     */
    public boolean existeNombreDuplicado(String nombre, ComplejoDeportivo complejoDeportivo) {
        if (nombre == null || complejoDeportivo == null) {
            return false;
        }
        return repositorioSalon.existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrue(nombre, complejoDeportivo);
    }

    /**
     * Verifica si existe un salón con el mismo nombre en el complejo, excluyendo un ID específico (útil para actualización)
     * @param nombre Nombre a verificar
     * @param complejoDeportivo Complejo deportivo
     * @param idExcluir ID del salón a excluir de la búsqueda
     * @return true si existe un duplicado, false si no existe
     */
    public boolean existeNombreDuplicadoExcluyendoId(String nombre, ComplejoDeportivo complejoDeportivo, Long idExcluir) {
        if (nombre == null || complejoDeportivo == null || idExcluir == null) {
            return false;
        }
        return repositorioSalon.existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrueAndIdNot(nombre, complejoDeportivo, idExcluir);
    }

    public void guardarSalon(Salon salon) {
        try {
            if (salon == null) {
                throw new IllegalArgumentException("El salón no puede ser nulo");
            }
            if (salon.getNombre() != null) {
                salon.setNombre(servicioNormalizacion.normalizarNombreApellido(salon.getNombre()));
            }

            if (salon.getPrecioPorHora() < 0) {
                throw new IllegalArgumentException("El precio por hora del salón no puede ser negativo");

            }
            salon.setActivo(true);

            if (salon.getComplejoDeportivo() == null) {
                throw new IllegalArgumentException("El salón debe estar asociado a un complejo deportivo");
            }

            repositorioSalon.save(salon);
        
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error al guardar el salon: " + e.getMessage(), e);
        }
        
    }

    public void eliminarSalon(Long id) {
        Salon salon = repositorioSalon.findById(id).orElseThrow(() -> new IllegalArgumentException("Salón no encontrado con ID: " + id));
        salon.setActivo(false);
        repositorioSalon.save(salon);
    }

    public void actualizarSalon(Salon salon) {
        Salon existente = repositorioSalon.findById(salon.getId()).orElseThrow(() -> new IllegalArgumentException("Salón no encontrado con ID: " + salon.getId()));
        if (salon.getNombre() != null) {
            existente.setNombre(servicioNormalizacion.normalizarNombreApellido(salon.getNombre()));
        }
        existente.setCapacidad(salon.getCapacidad());
        existente.setPrecioPorHora(salon.getPrecioPorHora());
        existente.setMetrosCuadrados(salon.getMetrosCuadrados());
        existente.setEstaClimatizado(salon.isEstaClimatizado());
        existente.setEstadoOperativo(salon.getEstadoOperativo());
        existente.setActivo(salon.getActivo());
        repositorioSalon.save(existente);
    }

}
