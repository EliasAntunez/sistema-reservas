package com.example.tureserva.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tureserva.modelo.HorarioComplejo;
import com.example.tureserva.modelo.enums.DiaSemana;
import com.example.tureserva.modelo.ComplejoDeportivo;
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
        return repositorioHorarioComplejo.findByComplejoDeportivo(complejo);
    }

    /**
     * Obtener TODOS los horarios activos de un complejo para un día específico
     * (Permite múltiples rangos horarios por día)
     */
    public List<HorarioComplejo> obtenerTodosHorariosPorComplejoYDia(ComplejoDeportivo complejo, DiaSemana diaSemana) {
        return repositorioHorarioComplejo.findAllByComplejoDeportivoAndDiaSemana(complejo, diaSemana);
    }

    /**
     * Obtener el primer horario de un día (útil para compatibilidad con código antiguo)
     * NOTA: Si hay múltiples horarios para el día, solo retorna el primero
     */
    public Optional<HorarioComplejo> obtenerHorarioPorComplejoYDia(ComplejoDeportivo complejo, DiaSemana diaSemana) {
        return repositorioHorarioComplejo.findByComplejoDeportivoAndDiaSemana(complejo, diaSemana);
    }

    public Optional<HorarioComplejo> obtenerPorId(Long id) {
        return repositorioHorarioComplejo.findById(id);
    }

    public HorarioComplejo guardarHorario(HorarioComplejo horario) {
        String error = validarHorarioBasico(horario);
        if (error != null) {
            throw new RuntimeException(error);
        }

        if (tieneSolapamiento(horario)) {
            throw new RuntimeException("El horario se solapa con otro horario existente del mismo día");
        }

        return repositorioHorarioComplejo.save(horario);
    }

    public HorarioComplejo actualizarHorario(HorarioComplejo horario) {
        String error = validarHorarioBasico(horario);
        if (error != null) {
            throw new RuntimeException(error);
        }

        if (tieneSolapamientoExcluyendo(horario, horario.getId())) {
            throw new RuntimeException("El horario se solapa con otro horario existente del mismo día");
        }

        return repositorioHorarioComplejo.save(horario);
    }

    /**
     * Eliminar horario (baja física)
     */
    public void eliminarHorario(Long horarioId) {
        repositorioHorarioComplejo.deleteById(horarioId);
    }

    public boolean existeHorarioParaDia(ComplejoDeportivo complejo, DiaSemana dia) {
        return repositorioHorarioComplejo.existsByComplejoDeportivoAndDiaSemana(complejo, dia);
    }

    public boolean validarHorario(HorarioComplejo horario) {
        return validarHorarioBasico(horario) == null;
    }

    public boolean estaAbierto(ComplejoDeportivo complejo, DiaSemana dia, LocalTime hora) {
        List<HorarioComplejo> horariosDelDia = obtenerTodosHorariosPorComplejoYDia(complejo, dia);

        for (HorarioComplejo horario : horariosDelDia) {
            if (estaEnRango(hora, horario.getHoraApertura(), horario.getHoraCierre())) {
                return true;
            }
        }

        return false;
    }

    public long contarHorariosConfigurados(ComplejoDeportivo complejo) {
        return repositorioHorarioComplejo.countByComplejoDeportivo(complejo);
    }

    public void crearHorariosDefecto(ComplejoDeportivo complejo) {
        LocalTime aperturaLV = LocalTime.of(16, 0);
        LocalTime cierreLV = LocalTime.of(00, 00);

        LocalTime aperturaSD = LocalTime.of(15, 00);
        LocalTime cierreSD = LocalTime.of(00, 00);

        for (DiaSemana dia : DiaSemana.values()) {
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

                repositorioHorarioComplejo.save(horario);
            }
        }
    }

    // ================================================================
    // ===================== MÉTODOS PRIVADOS =========================
    // ================================================================

    private String validarHorarioBasico(HorarioComplejo horario) {
        if (horario == null) return "Horario no válido";
        if (horario.getDiaSemana() == null) return "Debe seleccionar un día de la semana";
        if (horario.getHoraApertura() == null) return "Debe especificar hora de apertura";
        if (horario.getHoraCierre() == null) return "Debe especificar hora de cierre";

        // Permitir cierres después de medianoche (por ejemplo 02:00)
        // Solo invalidar si apertura y cierre son iguales
        if (horario.getHoraApertura().equals(horario.getHoraCierre())) {
            return "La hora de cierre no puede ser igual a la de apertura";
        }

        return null;
    }

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

    private boolean tieneSolapamientoExcluyendo(HorarioComplejo horario, Long idExcluir) {
        List<HorarioComplejo> horariosExistentes = obtenerTodosHorariosPorComplejoYDia(
                horario.getComplejoDeportivo(),
                horario.getDiaSemana()
        );

        for (HorarioComplejo existente : horariosExistentes) {
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
     * Permite horarios que pasan la medianoche (por ejemplo 15:00–02:00)
     */
    private boolean seSuperponen(HorarioComplejo h1, HorarioComplejo h2) {
        // Convertimos ambos a rangos "extendidos" en minutos desde el inicio del día
        int inicio1 = h1.getHoraApertura().toSecondOfDay();
        int fin1 = h1.getHoraCierre().toSecondOfDay();
        int inicio2 = h2.getHoraApertura().toSecondOfDay();
        int fin2 = h2.getHoraCierre().toSecondOfDay();

        // Si el horario pasa medianoche, le sumamos 24h al fin
        if (fin1 <= inicio1) fin1 += 24 * 3600;
        if (fin2 <= inicio2) fin2 += 24 * 3600;

        // Se superponen si los rangos se cruzan
        return (inicio1 < fin2 && inicio2 < fin1);
    }

    private boolean estaEnRango(LocalTime hora, LocalTime inicio, LocalTime fin) {
        int h = hora.toSecondOfDay();
        int i = inicio.toSecondOfDay();
        int f = fin.toSecondOfDay();

        if (f <= i) f += 24 * 3600; // Cruza medianoche
        if (h < i) h += 24 * 3600;  // Hora después de medianoche

        return h >= i && h < f;
    }
}
