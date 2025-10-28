package com.example.tureserva.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.tureserva.repositorio.RepositorioPoliticaSenia;
import com.example.tureserva.modelo.PoliticaSenia;
import com.example.tureserva.modelo.ComplejoDeportivo;
import java.util.Optional;

@Service
public class ServicioPoliticaSenia {
    
    @Autowired
    private RepositorioPoliticaSenia repositorioPoliticaSenia;

    @Autowired
    private ServicioComplejoDeportivo servicioComplejoDeportivo;

    //guardar politica de seña
    public void guardarPoliticaSenia(PoliticaSenia politicaSenia) {
        repositorioPoliticaSenia.save(politicaSenia);
    }

    //obtener politica de seña por id
    public PoliticaSenia obtenerPoliticaSeniaPorId(Long id) {
        return repositorioPoliticaSenia.findById(id).orElse(null);
    }

    //eliminar politica de seña por id
    public void darDeBajaPoliticaSenia(Long id) {
        Optional<PoliticaSenia> politicaSeniaOpt = repositorioPoliticaSenia.findById(id);
        if (politicaSeniaOpt.isPresent()) {
            PoliticaSenia politicaSenia = politicaSeniaOpt.get();
            politicaSenia.setActivo(false);
            repositorioPoliticaSenia.save(politicaSenia);
        }
        
    }

    //actualizar politica de seña
    public void actualizarPoliticaSenia(PoliticaSenia politicaSenia) {
        repositorioPoliticaSenia.save(politicaSenia);
    }

    //listar todas las politicas de seña de un complejo cuyo id esta en la url
    public java.util.List<PoliticaSenia> listarPoliticasSeniaPorComplejoId(Long complejoId) {
        Optional<ComplejoDeportivo> complejoDeportivo = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoDeportivo.isPresent()) {
            return repositorioPoliticaSenia.findByComplejoDeportivoAndActivoTrue(complejoDeportivo.get());
        }
        return java.util.Collections.emptyList();
    }

    //obtener complejoId por politicaId
    public Long obtenerComplejoIdPorPoliticaId(Long politicaId) {
        Optional<PoliticaSenia> politicaSenia = repositorioPoliticaSenia.findById(politicaId);
        if (politicaSenia.isPresent()) {
            return politicaSenia.get().getComplejoDeportivo().getId_complejo();
        }
        return null;
    }
}
