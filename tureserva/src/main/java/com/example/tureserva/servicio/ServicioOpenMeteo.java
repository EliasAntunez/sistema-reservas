package com.example.tureserva.servicio;

import com.example.tureserva.servicio.dto.RespuestaClimaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Servicio para consultar la API de Open-Meteo y obtener pronósticos del clima.
 * Documentación: https://open-meteo.com/en/docs
 */
@Service
public class ServicioOpenMeteo {
    
    private static final Logger log = LoggerFactory.getLogger(ServicioOpenMeteo.class);
    
    private static final String OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast";
    
    /**
     * Códigos WMO que indican condiciones climáticas adversas (lluvia, tormenta, nieve).
     * Referencia: https://www.nodc.noaa.gov/archive/arc0021/0002199/1.1/data/0-data/HTML/WMO-CODE/WMO4677.HTM
     */
    private static final List<Integer> CODIGOS_MAL_CLIMA = Arrays.asList(
        51, 53, 55,  // Llovizna (ligera, moderada, densa)
        61, 63, 65,  // Lluvia (ligera, moderada, fuerte)
        66, 67,      // Lluvia helada
        71, 73, 75,  // Nieve
        77,          // Granos de nieve
        80, 81, 82,  // Chubascos de lluvia
        85, 86,      // Chubascos de nieve
        95,          // Tormenta eléctrica
        96, 99       // Tormenta con granizo
    );
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public ServicioOpenMeteo() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Consulta el pronóstico del clima para una ubicación y fecha/hora específica.
     * 
     * @param latitud Latitud de la ubicación
     * @param longitud Longitud de la ubicación
     * @param fechaHora Fecha y hora de la reserva
     * @param umbralProbabilidad Umbral de probabilidad de lluvia para considerar mal clima (0-100)
     * @return RespuestaClimaDTO con información del clima
     */
    public RespuestaClimaDTO consultarClima(BigDecimal latitud, BigDecimal longitud, 
                                            LocalDateTime fechaHora, Integer umbralProbabilidad) {
        try {
            // Formatear fecha para la API (ISO 8601: yyyy-MM-dd)
            String fecha = fechaHora.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String fechaFin = fechaHora.toLocalDate().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            // Construir URL de la API
            String url = String.format(
                "%s?latitude=%s&longitude=%s&start_date=%s&end_date=%s&hourly=temperature_2m,precipitation_probability,weathercode&timezone=auto",
                OPEN_METEO_URL,
                latitud.toString(),
                longitud.toString(),
                fecha,
                fechaFin
            );
            
            log.debug("Consultando clima en Open-Meteo: {}", url);
            
            // Realizar petición HTTP
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                log.warn("Respuesta vacía de Open-Meteo");
                return crearRespuestaError();
            }
            
            // Parsear JSON
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode hourlyNode = rootNode.get("hourly");
            
            if (hourlyNode == null) {
                log.warn("No se encontró data horaria en la respuesta de Open-Meteo");
                return crearRespuestaError();
            }
            
            // Obtener arrays de datos
            JsonNode timesNode = hourlyNode.get("time");
            JsonNode precipProbNode = hourlyNode.get("precipitation_probability");
            JsonNode weatherCodeNode = hourlyNode.get("weathercode");
            JsonNode tempNode = hourlyNode.get("temperature_2m");
            
            // Buscar el índice más cercano a la hora de la reserva
            int indice = encontrarIndiceMasCercano(timesNode, fechaHora);
            
            if (indice == -1) {
                log.warn("No se encontró data para la fecha/hora solicitada");
                return crearRespuestaError();
            }
            
            // Extraer valores
            Integer probabilidadLluvia = precipProbNode.get(indice).asInt();
            Integer codigoClima = weatherCodeNode.get(indice).asInt();
            Double temperatura = tempNode.get(indice).asDouble();
            
            // Mapear código WMO a tipo de precipitación
            com.example.tureserva.servicio.dto.TipoPrecipitacion tipo = mapCodigoATipo(codigoClima);

            // Determinar si hay mal clima con el umbral proporcionado (compatibilidad)
            boolean hayMalClima = probabilidadLluvia >= (umbralProbabilidad != null ? umbralProbabilidad : 0);
            
            String descripcion = obtenerDescripcionClima(codigoClima);
            
            log.info("Clima consultado: temp={}°C, prob={}%, codigo={}, malClima={}", 
                     temperatura, probabilidadLluvia, codigoClima, hayMalClima);
            
            return new RespuestaClimaDTO(
                hayMalClima,
                probabilidadLluvia,
                codigoClima,
                descripcion,
                tipo,
                null,
                temperatura
            );
            
        } catch (Exception e) {
            log.error("Error al consultar API de Open-Meteo: {}", e.getMessage(), e);
            return crearRespuestaError();
        }
    }
    
    /**
     * Encuentra el índice del array de datos más cercano a la hora solicitada.
     */
    private int encontrarIndiceMasCercano(JsonNode timesNode, LocalDateTime fechaHora) {
        
        for (int i = 0; i < timesNode.size(); i++) {
            String timeStr = timesNode.get(i).asText();
            // Comparar solo fecha y hora (ignorar minutos/segundos)
            if (timeStr.startsWith(fechaHora.toLocalDate().toString()) &&
                timeStr.contains(String.format("T%02d:", fechaHora.getHour()))) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * Obtiene una descripción legible del código WMO.
     */
    private String obtenerDescripcionClima(Integer codigo) {
        if (codigo == null) return "Desconocido";
        
        return switch (codigo) {
            case 0 -> "Despejado";
            case 1 -> "Parcialmente nublado";
            case 2 -> "Parcialmente nublado";
            case 3 -> "Nublado";
            case 45, 48 -> "Niebla";
            case 51, 53, 55 -> "Llovizna";
            case 56, 57 -> "Llovizna helada";
            case 61 -> "Lluvia ligera";
            case 63 -> "Lluvia moderada";
            case 65 -> "Lluvia fuerte";
            case 66, 67 -> "Lluvia helada";
            case 71, 73, 75 -> "Nieve";
            case 77 -> "Granos de nieve";
            case 80, 81, 82 -> "Chaparrón";
            case 85, 86 -> "Chaparrón de nieve";
            case 95 -> "Tormenta eléctrica";
            case 96, 99 -> "Tormenta con granizo";
            default -> "Código desconocido: " + codigo;
        };
    }
    
    /**
     * Crea una respuesta de error por defecto.
     */
    private RespuestaClimaDTO crearRespuestaError() {
        return new RespuestaClimaDTO(
            false,  // Asumir no hay mal clima en caso de error (evitar alertas falsas)
            0,
            null,
            "Error al consultar el clima",
            com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO,
            null,
            null
        );
    }

    private com.example.tureserva.servicio.dto.TipoPrecipitacion mapCodigoATipo(Integer codigo) {
        if (codigo == null) return com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO;

        return switch (codigo) {
            case 51, 53, 55, 56, 57 -> com.example.tureserva.servicio.dto.TipoPrecipitacion.LLOVIZNA;
            case 61, 63, 65, 66, 67 -> com.example.tureserva.servicio.dto.TipoPrecipitacion.LLUVIA;
            case 80, 81, 82, 85, 86 -> com.example.tureserva.servicio.dto.TipoPrecipitacion.CHAPARRON;
            case 95, 96, 99 -> com.example.tureserva.servicio.dto.TipoPrecipitacion.TORMENTA;
            case 71, 73, 75 -> com.example.tureserva.servicio.dto.TipoPrecipitacion.NIEVE;
            default -> com.example.tureserva.servicio.dto.TipoPrecipitacion.OTRO;
        };
    }
}
