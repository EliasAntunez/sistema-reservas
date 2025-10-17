package com.example.tureserva.servicio;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Localidad;
import com.example.tureserva.modelo.Pais;
import com.example.tureserva.modelo.Provincia;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.repositorio.RepositorioLocalidad;
import com.example.tureserva.repositorio.RepositorioPais;
import com.example.tureserva.repositorio.RepositorioProvincia;
import com.example.tureserva.repositorio.RepositorioAdministradorComplejo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ServicioComplejoDeportivo {

    private final RepositorioComplejoDeportivo repositorioComplejoDeportivo;
    private final RepositorioPais repositorioPais;
    private final RepositorioProvincia repositorioProvincia;
    private final RepositorioLocalidad repositorioLocalidad;
    private final RepositorioAdministradorComplejo repositorioAdministradorComplejo;

    public ServicioComplejoDeportivo(RepositorioComplejoDeportivo repositorioComplejoDeportivo,
                                   RepositorioPais repositorioPais,
                                   RepositorioProvincia repositorioProvincia,
                                   RepositorioLocalidad repositorioLocalidad,
                                   RepositorioAdministradorComplejo repositorioAdministradorComplejo) {
        this.repositorioComplejoDeportivo = repositorioComplejoDeportivo;
        this.repositorioPais = repositorioPais;
        this.repositorioProvincia = repositorioProvincia;
        this.repositorioLocalidad = repositorioLocalidad;
        this.repositorioAdministradorComplejo = repositorioAdministradorComplejo;
    }

    /**
     * Crear un nuevo complejo deportivo (simplificado)
     */
    public ComplejoDeportivo crearComplejoSimple(String nombreComplejo, String direccion, 
                                               Long localidadId, Long administradorId) {
        
        // Validar que existan las entidades relacionadas
        Localidad localidad = repositorioLocalidad.findById(localidadId)
            .orElseThrow(() -> new RuntimeException("Localidad no encontrada"));
        
        AdministradorComplejo administrador = repositorioAdministradorComplejo.findById(administradorId)
            .orElseThrow(() -> new RuntimeException("Administrador no encontrado"));

        // Crear el complejo usando los datos de la localidad
        ComplejoDeportivo complejo = new ComplejoDeportivo();
        complejo.setNombre_complejo(nombreComplejo);
        complejo.setDireccion_complejo(direccion);
        complejo.setPais(localidad.getProvincia().getPais());
        complejo.setProvincia(localidad.getProvincia());
        complejo.setLocalidad(localidad);
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
        // Primero obtenemos Argentina
        Pais argentina = repositorioPais.findByNombre("Argentina")
            .orElseThrow(() -> new RuntimeException("País Argentina no encontrado en la base de datos"));
        
        // Luego obtenemos Misiones
        Provincia misiones = repositorioProvincia.findByNombreAndPaisId("Misiones", argentina.getId())
            .orElseThrow(() -> new RuntimeException("Provincia Misiones no encontrada en la base de datos"));
        
        // Retornamos las localidades de Misiones
        return repositorioLocalidad.findByProvinciaId(misiones.getId());
    }

    /**
     * Obtener todos los complejos activos
     */
    public List<ComplejoDeportivo> obtenerComplejosActivos() {
        return repositorioComplejoDeportivo.findByActivoTrue();
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
                                      Long paisId, Long provinciaId, Long localidadId) {
        ComplejoDeportivo complejo = repositorioComplejoDeportivo.findById(id)
            .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

        // Actualizar datos básicos
        complejo.setNombre_complejo(nombreComplejo);
        complejo.setDireccion_complejo(direccion);

        // Actualizar ubicación si cambió
        if (!complejo.getPais().getId().equals(paisId)) {
            Pais pais = repositorioPais.findById(paisId)
                .orElseThrow(() -> new RuntimeException("País no encontrado"));
            complejo.setPais(pais);
        }

        if (!complejo.getProvincia().getId().equals(provinciaId)) {
            Provincia provincia = repositorioProvincia.findById(provinciaId)
                .orElseThrow(() -> new RuntimeException("Provincia no encontrada"));
            complejo.setProvincia(provincia);
        }

        if (!complejo.getLocalidad().getId().equals(localidadId)) {
            Localidad localidad = repositorioLocalidad.findById(localidadId)
                .orElseThrow(() -> new RuntimeException("Localidad no encontrada"));
            complejo.setLocalidad(localidad);
        }

        return repositorioComplejoDeportivo.save(complejo);
    }

    /**
     * Obtener todos los países
     */
    public List<Pais> obtenerPaises() {
        return repositorioPais.findAll();
    }

    /**
     * Obtener provincias por país
     */
    public List<Provincia> obtenerProvinciasPorPais(Long paisId) {
        return repositorioProvincia.findByPaisId(paisId);
    }

    /**
     * Obtener localidades por provincia
     */
    public List<Localidad> obtenerLocalidadesPorProvincia(Long provinciaId) {
        return repositorioLocalidad.findByProvinciaId(provinciaId);
    }
}