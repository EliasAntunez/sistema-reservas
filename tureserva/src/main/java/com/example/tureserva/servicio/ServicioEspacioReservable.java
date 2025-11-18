package com.example.tureserva.servicio;

import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.repositorio.RepositorioEspacioReservable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServicioEspacioReservable {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicioEspacioReservable.class);

    private final RepositorioEspacioReservable repositorioEspacioReservable;
    private final RepositorioComplejoDeportivo repositorioComplejo;

    public ServicioEspacioReservable(RepositorioEspacioReservable repositorioEspacioReservable,
                                     RepositorioComplejoDeportivo repositorioComplejo) {
        this.repositorioEspacioReservable = repositorioEspacioReservable;
        this.repositorioComplejo = repositorioComplejo;
    }

    /**
     * Obtiene un espacio reservable por ID
     */
    public Optional<EspacioReservable> obtenerPorId(Long id) {
        return repositorioEspacioReservable.findById(id);
    }

    /**
     * Guarda o actualiza un espacio reservable
     */
    public EspacioReservable guardar(EspacioReservable espacioReservable) {
        return repositorioEspacioReservable.save(espacioReservable);
    }
    
    /**
     * Obtiene todos los espacios activos y disponibles de un complejo.
     * Precarga relaciones LAZY usando JOIN FETCH.
     */
    public List<EspacioReservable> obtenerEspaciosActivosDeComplejo(Long idComplejo) {
        Optional<ComplejoDeportivo> complejoOpt = repositorioComplejo.findById(idComplejo);
        if (complejoOpt.isEmpty()) {
            return List.of();
        }
        
        // Usar query con JOIN FETCH para evitar LazyInitializationException
        List<EspacioReservable> todosLosEspacios = repositorioEspacioReservable
            .findByComplejoDeportivoWithRelations(complejoOpt.get());
        
        logger.debug("Espacios encontrados en complejo {}: {}", idComplejo, todosLosEspacios.size());
        
        List<EspacioReservable> espaciosFiltrados = todosLosEspacios.stream()
            .filter(EspacioReservable::estaDisponibleParaReservas)
            .collect(Collectors.toList());
        
        logger.debug("Espacios disponibles para reservas: {}", espaciosFiltrados.size());
        
        return espaciosFiltrados;
    }
    
    /**
     * Obtiene todos los espacios de un complejo (sin filtrar por estado)
     */
    public List<EspacioReservable> obtenerEspaciosDeComplejo(Long idComplejo) {
        Optional<ComplejoDeportivo> complejoOpt = repositorioComplejo.findById(idComplejo);
        if (complejoOpt.isEmpty()) {
            return List.of();
        }
        
        return repositorioEspacioReservable.findByComplejoDeportivo(complejoOpt.get());
    }
}
