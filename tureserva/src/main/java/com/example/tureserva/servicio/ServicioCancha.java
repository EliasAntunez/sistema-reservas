package com.example.tureserva.servicio;

import org.springframework.stereotype.Service;
import com.example.tureserva.repositorio.RepositorioCancha;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.modelo.Cancha;
import com.example.tureserva.modelo.ComplejoDeportivo;
import java.util.Optional;

@Service
public class ServicioCancha {

    private final RepositorioCancha repositorioCancha;
    private final RepositorioComplejoDeportivo repositorioComplejo;

    public ServicioCancha(RepositorioCancha repositorioCancha, RepositorioComplejoDeportivo repositorioComplejo) {
        this.repositorioCancha = repositorioCancha;
        this.repositorioComplejo = repositorioComplejo;
    }

    public org.springframework.data.domain.Page<Cancha> listarCanchasPorComplejoPaginado(Long idComplejo, int page, int size) {
        ComplejoDeportivo complejo = repositorioComplejo.findById(idComplejo).orElse(null);
        if (complejo == null) return org.springframework.data.domain.Page.empty();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size);
        return repositorioCancha.findByComplejoDeportivoAndActivoTrue(complejo, pageable);
    }

    // --- Métodos auxiliares de normalización (ubicados después del constructor) ---
    private String normalizarNombre(String valor) {
        if (valor == null || valor.isEmpty()) return valor;
        String[] palabras = valor.trim().toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String palabra : palabras) {
            if (palabra.length() > 0) {
                sb.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1));
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    // No se normaliza DNI en Cancha (no aplica)

    public Optional<Cancha> obtenerPorId(Long id) {
        return repositorioCancha.findById(id);
    }

    public int contarCanchasPorComplejo(Long idComplejo) {
        ComplejoDeportivo complejo = repositorioComplejo.findById(idComplejo).orElse(null);
        if (complejo == null) return 0;
        return repositorioCancha.findByComplejoDeportivoAndActivoTrue(complejo).size();
    }

    /**
     * Verifica si ya existe una cancha activa con el mismo nombre en el complejo
     * @param nombre Nombre a verificar
     * @param complejoDeportivo Complejo deportivo
     * @return true si existe, false si no existe
     */
    public boolean existeNombreDuplicado(String nombre, ComplejoDeportivo complejoDeportivo) {
        if (nombre == null || complejoDeportivo == null) {
            return false;
        }
        return repositorioCancha.existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrue(nombre, complejoDeportivo);
    }

    /**
     * Verifica si existe una cancha con el mismo nombre en el complejo, excluyendo un ID específico (útil para actualización)
     * @param nombre Nombre a verificar
     * @param complejoDeportivo Complejo deportivo
     * @param idExcluir ID de la cancha a excluir de la búsqueda
     * @return true si existe un duplicado, false si no existe
     */
    public boolean existeNombreDuplicadoExcluyendoId(String nombre, ComplejoDeportivo complejoDeportivo, Long idExcluir) {
        if (nombre == null || complejoDeportivo == null || idExcluir == null) {
            return false;
        }
        return repositorioCancha.existsByNombreIgnoreCaseAndComplejoDeportivoAndActivoTrueAndIdNot(nombre, complejoDeportivo, idExcluir);
    }

    public void guardarCancha(Cancha cancha) {
        try {
            if (cancha == null) {
                throw new IllegalArgumentException("La cancha no puede ser nula");
            }
            if (cancha.getNombre() != null) {
                cancha.setNombre(normalizarNombre(cancha.getNombre()));
            }

            if (cancha.getPrecioPorHora() < 0) {
                throw new IllegalArgumentException("El precio por hora de la cancha no puede ser negativo");
                
            }
            cancha.setActivo(true);

            if (cancha.getComplejoDeportivo() == null) {
                throw new IllegalArgumentException("La cancha debe estar asociada a un complejo deportivo");
            }

            repositorioCancha.save(cancha);
        
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error al guardar la cancha: " + e.getMessage(), e);
        }
        
    }

    public void eliminarCancha(Long id) {
        Cancha cancha = repositorioCancha.findById(id).orElseThrow(() -> new IllegalArgumentException("Cancha no encontrada con ID: " + id));
        cancha.setActivo(false);
        repositorioCancha.save(cancha);
    }

    public void actualizarCancha(Cancha cancha) {
        Cancha existente = repositorioCancha.findById(cancha.getId()).orElseThrow(() -> new IllegalArgumentException("Cancha no encontrada con ID: " + cancha.getId()));
        if (cancha.getNombre() != null) {
            existente.setNombre(normalizarNombre(cancha.getNombre()));
        }
        existente.setCapacidad(cancha.getCapacidad());
        existente.setPrecioPorHora(cancha.getPrecioPorHora());
        existente.setEsTechada(cancha.getEsTechada());
        existente.setTipoPiso(cancha.getTipoPiso());
        existente.setEstadoOperativo(cancha.getEstadoOperativo());
        existente.setActivo(cancha.getActivo());
        repositorioCancha.save(existente);
    }
}
