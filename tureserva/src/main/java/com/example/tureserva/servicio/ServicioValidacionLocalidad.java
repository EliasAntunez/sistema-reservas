package com.example.tureserva.servicio;

import com.example.tureserva.modelo.Localidad;
import com.example.tureserva.repositorio.RepositorioLocalidad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Servicio para validar localidades basándose en coordenadas geográficas.
 * Verifica si una dirección pertenece a una localidad con cobertura en el sistema.
 */
@Service
@Transactional(readOnly = true)
public class ServicioValidacionLocalidad {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicioValidacionLocalidad.class);
    
    // Radio de tolerancia en kilómetros (área de cobertura por localidad)
    private static final double RADIO_COBERTURA_KM = 50.0;
    
    /**
     * Coordenadas de referencia para las localidades soportadas.
     * Estos valores se usan cuando la BD no tiene coordenadas almacenadas.
     * Fuente: OpenStreetMap
     */
    private static final Map<String, double[]> COORDENADAS_LOCALIDADES = Map.of(
        "Apóstoles", new double[]{-27.9097, -55.7581},
        "Posadas", new double[]{-27.3621, -55.9008}
    );
    
    private final RepositorioLocalidad repositorioLocalidad;
    
    public ServicioValidacionLocalidad(RepositorioLocalidad repositorioLocalidad) {
        this.repositorioLocalidad = repositorioLocalidad;
    }
    
    /**
     * Valida si unas coordenadas están dentro del área de cobertura de alguna localidad.
     * Busca la localidad más cercana y verifica si está dentro del radio permitido.
     * 
     * @param latitud Latitud de la dirección a validar
     * @param longitud Longitud de la dirección a validar
     * @return ResultadoValidacion con el resultado y mensaje
     */
    public ResultadoValidacion validarCobertura(BigDecimal latitud, BigDecimal longitud) {
        if (latitud == null || longitud == null) {
            return new ResultadoValidacion(false, null, null,
                "Coordenadas inválidas", 0.0);
        }
        
        List<Localidad> localidadesDisponibles = repositorioLocalidad.findAll();
        
        if (localidadesDisponibles.isEmpty()) {
            logger.error("ERROR CRÍTICO: No hay localidades configuradas en la base de datos");
            return new ResultadoValidacion(false, null, null,
                "Error de configuración del sistema. Contacte al administrador.", 0.0);
        }
        
        // Buscar la localidad más cercana
        Localidad localidadMasCercana = null;
        double distanciaMinima = Double.MAX_VALUE;
        
        for (Localidad localidad : localidadesDisponibles) {
            double[] coordenadas = obtenerCoordenadasLocalidad(localidad);
            if (coordenadas == null) {
                logger.warn("Localidad '{}' sin coordenadas de referencia configuradas", localidad.getNombre());
                continue;
            }
            
            double distancia = calcularDistanciaHaversine(
                coordenadas[0], coordenadas[1],
                latitud.doubleValue(), longitud.doubleValue()
            );
            
            logger.debug("Distancia desde {}: {:.2f} km", localidad.getNombre(), distancia);
            
            if (distancia < distanciaMinima) {
                distanciaMinima = distancia;
                localidadMasCercana = localidad;
            }
        }
        
        if (localidadMasCercana == null) {
            return new ResultadoValidacion(false, null, null,
                "No se pudo determinar la localidad más cercana.", 0.0);
        }
        
        // Verificar si está dentro del radio de cobertura
        if (distanciaMinima <= RADIO_COBERTURA_KM) {
            return new ResultadoValidacion(
                true, 
                localidadMasCercana.getId(),
                localidadMasCercana.getNombre(),
                String.format("Ubicación válida en %s (%.1f km del centro)", 
                    localidadMasCercana.getNombre(), distanciaMinima),
                distanciaMinima
            );
        } else {
            // Construir mensaje con localidades disponibles
            String localidadesStr = localidadesDisponibles.stream()
                .map(Localidad::getNombre)
                .reduce((a, b) -> a + ", " + b)
                .orElse("ninguna localidad");
            
            return new ResultadoValidacion(
                false, 
                null,
                null,
                String.format("Lo sentimos, actualmente operamos en: %s. " +
                              "Esta ubicación está a %.1f km de %s (la más cercana).", 
                              localidadesStr, distanciaMinima, localidadMasCercana.getNombre()),
                distanciaMinima
            );
        }
    }
    
    /**
     * Obtiene las coordenadas de una localidad.
     * Prioriza coordenadas de la BD, si no existen usa las hardcodeadas.
     */
    private double[] obtenerCoordenadasLocalidad(Localidad localidad) {
        // TODO: Si en el futuro agregamos campos lat/lon a Localidad, usarlos primero
        // if (localidad.getLatitud() != null && localidad.getLongitud() != null) {
        //     return new double[]{localidad.getLatitud(), localidad.getLongitud()};
        // }
        
        return COORDENADAS_LOCALIDADES.get(localidad.getNombre());
    }
    
    /**
     * Calcula la distancia entre dos puntos geográficos usando la fórmula de Haversine.
     * 
     * @param lat1 Latitud del punto 1
     * @param lon1 Longitud del punto 1
     * @param lat2 Latitud del punto 2
     * @param lon2 Longitud del punto 2
     * @return Distancia en kilómetros
     */
    private double calcularDistanciaHaversine(double lat1, double lon1, double lat2, double lon2) {
        final double RADIO_TIERRA_KM = 6371.0;
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return RADIO_TIERRA_KM * c;
    }
    
    /**
     * DTO para el resultado de la validación
     */
    public static class ResultadoValidacion {
        private final boolean valido;
        private final Long localidadId;
        private final String nombreLocalidad;
        private final String mensaje;
        private final double distanciaKm;
        
        public ResultadoValidacion(boolean valido, Long localidadId, String nombreLocalidad, 
                                   String mensaje, double distanciaKm) {
            this.valido = valido;
            this.localidadId = localidadId;
            this.nombreLocalidad = nombreLocalidad;
            this.mensaje = mensaje;
            this.distanciaKm = distanciaKm;
        }
        
        public boolean isValido() { return valido; }
        public Long getLocalidadId() { return localidadId; }
        public String getNombreLocalidad() { return nombreLocalidad; }
        public String getMensaje() { return mensaje; }
        public double getDistanciaKm() { return distanciaKm; }
    }
}
