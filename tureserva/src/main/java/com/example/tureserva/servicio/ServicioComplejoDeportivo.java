package com.example.tureserva.servicio;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Localidad;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.repositorio.RepositorioLocalidad;
import com.example.tureserva.repositorio.RepositorioAdministradorComplejo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ServicioComplejoDeportivo {

    private final RepositorioComplejoDeportivo repositorioComplejoDeportivo;
    private final RepositorioLocalidad repositorioLocalidad;
    private final RepositorioAdministradorComplejo repositorioAdministradorComplejo;

    public ServicioComplejoDeportivo(RepositorioComplejoDeportivo repositorioComplejoDeportivo,
                                   RepositorioLocalidad repositorioLocalidad,
                                   RepositorioAdministradorComplejo repositorioAdministradorComplejo) {
        this.repositorioComplejoDeportivo = repositorioComplejoDeportivo;
        this.repositorioLocalidad = repositorioLocalidad;
        this.repositorioAdministradorComplejo = repositorioAdministradorComplejo;
    }

    /**
     * Crear un nuevo complejo deportivo (simplificado)
     */
    public ComplejoDeportivo crearComplejoSimple(String nombreComplejo, String direccion, 
                                               Long localidadId, Long administradorId,
                                               java.math.BigDecimal latitud, java.math.BigDecimal longitud) {
        
        // Validar administrador (obligatorio)
        AdministradorComplejo administrador = repositorioAdministradorComplejo.findById(administradorId)
            .orElseThrow(() -> new RuntimeException("Administrador no encontrado"));

        // Validar localidad (opcional - solo si se proporciona)
        Localidad localidad = null;
        if (localidadId != null) {
            localidad = repositorioLocalidad.findById(localidadId)
                .orElseThrow(() -> new RuntimeException("Localidad no encontrada"));
        }

        // Crear el complejo
        ComplejoDeportivo complejo = new ComplejoDeportivo();
        complejo.setNombre_complejo(nombreComplejo);
        complejo.setDireccion_complejo(direccion);
        complejo.setLatitud(latitud);
        complejo.setLongitud(longitud);
        complejo.setLocalidad(localidad); // Puede ser null
        complejo.setAdministradorComplejo(administrador);
        complejo.setActivo(true);

        return repositorioComplejoDeportivo.save(complejo);
    }

    /**
     * Obtener todas las localidades disponibles (temporal)
     */
    public List<Localidad> obtenerTodasLasLocalidades() {
        return repositorioLocalidad.findAll();
    }

    /**
     * Obtener todas las localidades de Misiones, Argentina
     */
    public List<Localidad> obtenerLocalidadesDeMisiones() {
        throw new UnsupportedOperationException("Este método ya no es compatible: el complejo solo se relaciona con localidad.");
    }

    /**
     * Obtener todos los complejos activos
     */
    public List<ComplejoDeportivo> obtenerComplejosActivos() {
        return repositorioComplejoDeportivo.findByActivoTrue();
    }

    /**
     * Obtener todos los complejos activos con paginación
     */
    public Page<ComplejoDeportivo> obtenerComplejosActivos(Pageable pageable) {
        return repositorioComplejoDeportivo.findByActivoTrue(pageable);
    }

    /**
     * Buscar complejos por nombre con paginación
     */
    public Page<ComplejoDeportivo> buscarPorNombre(String nombre, Pageable pageable) {
        return repositorioComplejoDeportivo.findByNombreComplejoContainingIgnoreCaseAndActivoTrue(nombre, pageable);
    }

    /**
     * Obtener complejo por ID
     */
    public Optional<ComplejoDeportivo> obtenerPorId(Long id) {
        return repositorioComplejoDeportivo.findById(id);
    }

    /**
     * Obtener complejos por administrador
     */
    public List<ComplejoDeportivo> obtenerComplejosPorAdministradorYActivoTrue(Long administradorId) {
        return repositorioComplejoDeportivo.findByAdministradorComplejo_IdAndActivoTrue(administradorId);
    }

    /**
     * Dar de baja un complejo (soft delete)
     */
    public void darDeBaja(Long id) {
        ComplejoDeportivo complejo = repositorioComplejoDeportivo.findById(id)
            .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));
        complejo.setActivo(false);
        repositorioComplejoDeportivo.save(complejo);
    }

    /**
     * Actualizar complejo
     */
    public ComplejoDeportivo actualizar(Long id, String nombreComplejo, String direccion,
                                      Long localidadId, java.math.BigDecimal latitud, java.math.BigDecimal longitud) {
        ComplejoDeportivo complejo = repositorioComplejoDeportivo.findById(id)
            .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

        // Actualizar datos básicos
        complejo.setNombre_complejo(nombreComplejo);
        complejo.setDireccion_complejo(direccion);
        complejo.setLatitud(latitud);
        complejo.setLongitud(longitud);

        // Actualizar ubicación si cambió
        if (!complejo.getLocalidad().getId().equals(localidadId)) {
            Localidad localidad = repositorioLocalidad.findById(localidadId)
                .orElseThrow(() -> new RuntimeException("Localidad no encontrada"));
            complejo.setLocalidad(localidad);
        }

        return repositorioComplejoDeportivo.save(complejo);
    }

    /**
     * Guardar o actualizar un complejo deportivo
     */
    public ComplejoDeportivo guardar(ComplejoDeportivo complejo) {
        return repositorioComplejoDeportivo.save(complejo);
    }

    /**
     * Obtener todos los países
     */
}