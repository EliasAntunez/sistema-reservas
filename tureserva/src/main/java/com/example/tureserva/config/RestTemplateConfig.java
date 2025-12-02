package com.example.tureserva.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuración de RestTemplate con timeouts para llamadas a APIs externas.
 * Evita bloqueos indefinidos y mejora la resiliencia del sistema.
 */
@Configuration
public class RestTemplateConfig {
    
    /**
     * RestTemplate configurado para Open-Meteo API con timeouts apropiados.
     * - Connect timeout: 5 segundos (tiempo máximo para establecer conexión)
     * - Read timeout: 10 segundos (tiempo máximo para leer respuesta)
     */
    @Bean(name = "openMeteoRestTemplate")
    public RestTemplate openMeteoRestTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        
        return builder
            // Buffering permite leer el response body múltiples veces (útil para logging)
            .requestFactory(() -> new BufferingClientHttpRequestFactory(factory))
            .build();
    }
}
