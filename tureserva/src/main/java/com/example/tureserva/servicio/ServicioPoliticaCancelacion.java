package com.example.tureserva.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.tureserva.anotacion.Auditable;
import com.example.tureserva.modelo.TipoEvento;
import com.example.tureserva.repositorio.RepositorioPoliticaCancelacion;
import com.example.tureserva.modelo.PoliticaCancelacion;
import com.example.tureserva.modelo.ComplejoDeportivo;
import java.util.Optional;
import java.util.List;

@Service
public class ServicioPoliticaCancelacion {
    @Autowired
    private RepositorioPoliticaCancelacion repositorioPoliticaCancelacion;

    @Autowired
    private ServicioComplejoDeportivo servicioComplejoDeportivo;

    // Guardar política de cancelación
    @Auditable(
        evento = TipoEvento.POLITICA_CANCELACION_CREADA,
        descripcion = "Política de cancelación creada",
        recursoTipo = "POLITICA_CANCELACION",
        complejoIdExpr = "#politicaCancelacion.complejoDeportivo.id_complejo",
        complejoNombreExpr = "#politicaCancelacion.complejoDeportivo.nombre_complejo",
        recursoIdExpr = "#politicaCancelacion.id",
        capturarDatosNuevos = true
    )
    public void guardarPoliticaCancelacion(PoliticaCancelacion politicaCancelacion) {
        repositorioPoliticaCancelacion.save(politicaCancelacion);
    }

    // Obtener política por id
    public PoliticaCancelacion obtenerPoliticaCancelacionPorId(Long id) {
        return repositorioPoliticaCancelacion.findById(id).orElse(null);
    }

    // Eliminar (dar de baja) política por id
    public void darDeBajaPoliticaCancelacion(Long id) {
        Optional<PoliticaCancelacion> politicaOpt = repositorioPoliticaCancelacion.findById(id);
        if (politicaOpt.isPresent()) {
            PoliticaCancelacion politica = politicaOpt.get();
            politica.setActivo(false);
            repositorioPoliticaCancelacion.save(politica);
        }
    }

    // Actualizar política
    @Auditable(
        evento = TipoEvento.POLITICA_CANCELACION_MODIFICADA,
        descripcion = "Política de cancelación modificada",
        recursoTipo = "POLITICA_CANCELACION",
        complejoIdExpr = "#politicaCancelacion.complejoDeportivo.id_complejo",
        complejoNombreExpr = "#politicaCancelacion.complejoDeportivo.nombre_complejo",
        recursoIdExpr = "#politicaCancelacion.id",
        capturarDatosAnteriores = true,
        capturarDatosNuevos = true
    )
    public void actualizarPoliticaCancelacion(PoliticaCancelacion politicaCancelacion) {
        repositorioPoliticaCancelacion.save(politicaCancelacion);
    }

    // Listar todas las políticas activas de un complejo
    public List<PoliticaCancelacion> listarPoliticasCancelacionPorComplejoId(Long complejoId) {
        Optional<ComplejoDeportivo> complejoOpt = servicioComplejoDeportivo.obtenerPorId(complejoId);
        if (complejoOpt.isPresent()) {
            return repositorioPoliticaCancelacion.findByComplejoDeportivoAndActivoTrue(complejoOpt.get());
        }
        return java.util.Collections.emptyList();
    }

    // Obtener complejoId por políticaId
    public Long obtenerComplejoIdPorPoliticaId(Long politicaId) {
        Optional<PoliticaCancelacion> politicaOpt = repositorioPoliticaCancelacion.findById(politicaId);
        if (politicaOpt.isPresent()) {
            return politicaOpt.get().getComplejoDeportivo().getId_complejo();
        }
        return null;
    }
}
