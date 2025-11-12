package com.example.tureserva.repositorio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.tureserva.modelo.EspacioReservable;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.ConfiguracionHorario;

@Repository
public interface RepositorioEspacioReservable extends JpaRepository<EspacioReservable, Long> {
    List<EspacioReservable> findByComplejoDeportivo(ComplejoDeportivo complejoDeportivo);
    List<EspacioReservable> findByHorarioPersonalizado(ConfiguracionHorario horarioPersonalizado);
}
