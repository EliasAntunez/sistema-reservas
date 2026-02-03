package com.example.tureserva.servicio;

import com.example.tureserva.servicio.dto.RespuestaClimaDTO;
import com.example.tureserva.servicio.dto.TipoPrecipitacion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Servicio para consultar la API de Open-Meteo y obtener pronósticos del clima.
 * Documentación: https://open-meteo.com/en/docs
 * Incluye validaciones robustas y manejo de errores mejorado.
 */
@Service
public class ServicioOpenMeteo {
    
    private static final Logger log = LoggerFactory.getLogger(ServicioOpenMeteo.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${clima.api.base-url:https://api.open-meteo.com/v1}")
    private String apiBaseUrl;
    
    /**
     * Constructor con inyección de dependencias.
     * Usa RestTemplate configurado con timeouts para evitar bloqueos.
     */
    public ServicioOpenMeteo(
            @Qualifier("openMeteoRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
            // Validar parámetros de entrada
            validarParametros(latitud, longitud, fechaHora);
            
            // Formatear fecha para la API (ISO 8601: yyyy-MM-dd)
            String fecha = fechaHora.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String fechaFin = fechaHora.toLocalDate().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            // Construir URL de la API de forma segura
            String url = UriComponentsBuilder
                .fromUriString(apiBaseUrl)
                .path("/forecast")
                .queryParam("latitude", latitud)
                .queryParam("longitude", longitud)
                .queryParam("start_date", fecha)
                .queryParam("end_date", fechaFin)
                .queryParam("hourly", "temperature_2m,precipitation_probability,weathercode,precipitation")
                .queryParam("timezone", "auto")
                .build()
                .toUriString();
            
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
            TipoPrecipitacion tipo = mapCodigoATipo(codigoClima);

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
            
        } catch (IllegalArgumentException e) {
            log.error("Parámetros inválidos: {}", e.getMessage());
            return crearRespuestaError("Parámetros inválidos: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("Error de conectividad con Open-Meteo: {}", e.getMessage());
            return crearRespuestaError("Error de conectividad: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado consultando clima: {}", e.getMessage(), e);
            return crearRespuestaError("Error inesperado: " + e.getMessage());
        }
    }
    
    /**
     * Valida que los parámetros de entrada sean correctos.
     */
    private void validarParametros(BigDecimal latitud, BigDecimal longitud, LocalDateTime fechaHora) {
        if (latitud == null || longitud == null) {
            throw new IllegalArgumentException("Latitud y longitud son obligatorios");
        }
        if (latitud.compareTo(new BigDecimal("-90")) < 0 || latitud.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("Latitud debe estar entre -90 y 90");
        }
        if (longitud.compareTo(new BigDecimal("-180")) < 0 || longitud.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("Longitud debe estar entre -180 y 180");
        }
        if (fechaHora == null) {
            throw new IllegalArgumentException("Fecha y hora son obligatorios");
        }
        if (fechaHora.isBefore(LocalDateTime.now().minusDays(1))) {
            throw new IllegalArgumentException("No se puede consultar clima del pasado");
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
     * hayMalClima = false para no generar alertas en caso de error.
     */
    private RespuestaClimaDTO crearRespuestaError() {
        return crearRespuestaError("Error al consultar el clima");
    }
    
    /**
     * Crea una respuesta de error con mensaje personalizado.
     */
    private RespuestaClimaDTO crearRespuestaError(String mensaje) {
        return new RespuestaClimaDTO(
            false,  // Asumir no hay mal clima en caso de error (evitar alertas falsas)
            0,
            null,
            "Error: " + mensaje,
            null,  // Sin tipo de precipitación en error
            null,
            null
        );
    }

    /**
     * Mapea código WMO a tipo de precipitación.
     * Retorna null si el código no corresponde a precipitación (despejado, nublado, niebla).
     */
    private TipoPrecipitacion mapCodigoATipo(Integer codigo) {
        if (codigo == null) return null;

        return switch (codigo) {
            case 51, 53, 55, 56, 57 -> TipoPrecipitacion.LLOVIZNA;
            case 61, 63, 65, 66, 67 -> TipoPrecipitacion.LLUVIA;
            case 80, 81, 82, 85, 86 -> TipoPrecipitacion.CHAPARRON;
            case 95, 96, 99 -> TipoPrecipitacion.TORMENTA;
            case 71, 73, 75 -> TipoPrecipitacion.NIEVE;
            default -> null;  // No es precipitación (despejado, nublado, niebla, etc)
        };
    }
}
