package com.example.tureserva.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
import com.example.tureserva.modelo.ServicioAdicional;
import com.example.tureserva.repositorio.RepositorioServicioAdicional;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.List;

@Service
@Validated
public class ServicioServicioAdicional {

    @Autowired
    private RepositorioServicioAdicional repositorioServicioAdicional;

    @Autowired
    private Validator validator;

    public ServicioAdicional findById(Long id) {
        return repositorioServicioAdicional.findById(id).orElse(null);
    }

    public java.util.Optional<ServicioAdicional> obtenerPorId(Long id) {
        return repositorioServicioAdicional.findById(id);
    }

    public List<ServicioAdicional> obtenerServiciosAdicionalesPorComplejoYActivoTrue(Long idComplejo) {
        return repositorioServicioAdicional.obtenerServiciosAdicionalesPorComplejoYActivoTrue(idComplejo);
    }

    // Guardar ServicioAdicional validando datos con jakarta validation
    @Transactional
    public void guardarServicioAdicional(@Valid ServicioAdicional servicioAdicional) {
        // Validación programática adicional: útil si la entidad tiene anotaciones de Bean Validation
        Set<ConstraintViolation<ServicioAdicional>> violations = validator.validate(servicioAdicional);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        // Normalización/ajustes antes de persistir (si aplica)
        if (servicioAdicional.getPrecio() != null) {
            servicioAdicional.setPrecio(servicioAdicional.getPrecio().setScale(2, java.math.RoundingMode.HALF_UP));
        }

        repositorioServicioAdicional.save(servicioAdicional);
    }

    @Transactional
    public void darDeBajaServicioAdicional(Long idServicio) {
        java.util.Optional<ServicioAdicional> servicioAdicionalOpt = repositorioServicioAdicional.findById(idServicio);
        if (servicioAdicionalOpt.isPresent()) {
            ServicioAdicional servicioAdicional = servicioAdicionalOpt.get();
            servicioAdicional.setActivo(false);
            repositorioServicioAdicional.save(servicioAdicional);
        }
    }

    // Pre-checks para evitar violaciones de unicidad y dar mejor feedback
    public boolean existeNombreEnComplejo(String nombre, com.example.tureserva.modelo.ComplejoDeportivo complejo) {
        return repositorioServicioAdicional.existsByNombreAndComplejoDeportivo(nombre, complejo);
    }

    public boolean existeNombreEnComplejoExcluyendoId(String nombre, com.example.tureserva.modelo.ComplejoDeportivo complejo, Long id) {
        return repositorioServicioAdicional.existsByNombreAndComplejoDeportivoAndIdNot(nombre, complejo, id);
    }
}
