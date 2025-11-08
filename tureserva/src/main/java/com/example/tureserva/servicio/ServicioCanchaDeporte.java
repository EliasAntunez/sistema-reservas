package com.example.tureserva.servicio;

import com.example.tureserva.modelo.CanchaDeporte;
import com.example.tureserva.modelo.Cancha;
import com.example.tureserva.modelo.Deporte;
import com.example.tureserva.repositorio.RepositorioCanchaDeporte;
import com.example.tureserva.repositorio.RepositorioCancha;
import com.example.tureserva.repositorio.RepositorioDeporte;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ServicioCanchaDeporte {

    private final RepositorioCanchaDeporte repositorioCanchaDeporte;
    private final RepositorioCancha repositorioCancha;
    private final RepositorioDeporte repositorioDeporte;

    public ServicioCanchaDeporte(RepositorioCanchaDeporte repositorioCanchaDeporte,
                                 RepositorioCancha repositorioCancha,
                                 RepositorioDeporte repositorioDeporte) {
        this.repositorioCanchaDeporte = repositorioCanchaDeporte;
        this.repositorioCancha = repositorioCancha;
        this.repositorioDeporte = repositorioDeporte;
    }

    /**
     * Obtiene todos los deportes asignados a una cancha
     */
    public List<CanchaDeporte> obtenerDeportesPorCancha(Long canchaId) {
        return repositorioCanchaDeporte.findByCanchaId(canchaId);
    }

    /**
     * Verifica si una cancha tiene deportes asignados
     */
    public boolean canchaTieneDeportes(Long canchaId) {
        return repositorioCanchaDeporte.existsByCanchaId(canchaId);
    }

    /**
     * Asigna deportes a una cancha (reemplaza los existentes)
     * Estrategia: Elimina todos los deportes actuales y asigna los nuevos
     */
    @Transactional
    public void asignarDeportesACancha(Long canchaId, List<Long> deporteIds) {
        // Validar que la cancha existe
        Cancha cancha = repositorioCancha.findById(canchaId)
                .orElseThrow(() -> new IllegalArgumentException("Cancha no encontrada con ID: " + canchaId));

        // Eliminar todas las asignaciones actuales
        repositorioCanchaDeporte.deleteByCanchaId(canchaId);

        // Asignar los nuevos deportes
        if (deporteIds != null && !deporteIds.isEmpty()) {
            for (Long deporteId : deporteIds) {
                Optional<Deporte> deporteOpt = repositorioDeporte.findById(deporteId);
                if (deporteOpt.isPresent()) {
                    CanchaDeporte canchaDeporte = new CanchaDeporte();
                    canchaDeporte.setCancha(cancha);
                    canchaDeporte.setDeporte(deporteOpt.get());
                    repositorioCanchaDeporte.save(canchaDeporte);
                }
            }
        }
    }

    /**
     * Elimina todas las asignaciones de deportes de una cancha
     */
    @Transactional
    public void eliminarDeportesDeCancha(Long canchaId) {
        repositorioCanchaDeporte.deleteByCanchaId(canchaId);
    }
}
