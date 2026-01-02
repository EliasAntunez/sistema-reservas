package com.example.tureserva.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Servicio centralizado para generación de códigos únicos.
 * Elimina duplicación de código en generación de códigos para reservas, clientes, etc.
 */
@Service
public class ServicioGeneradorCodigos {

    private static final Logger logger = LoggerFactory.getLogger(ServicioGeneradorCodigos.class);
    
    private static final int LONGITUD_CODIGO_DEFECTO = 6;
    private static final int MAX_INTENTOS_DEFECTO = 10;

    /**
     * Genera un código único con prefijo personalizado
     * 
     * @param prefijo Prefijo del código (ej: "RES", "CLI")
     * @param longitud Longitud del código UUID (caracteres después del prefijo)
     * @param validadorUnicidad Función que valida si el código ya existe (retorna true si existe)
     * @return Código único generado
     * @throws RuntimeException Si no se puede generar un código único después de MAX_INTENTOS
     */
    public String generarCodigoUnico(String prefijo, int longitud, Predicate<String> validadorUnicidad) {
        return generarCodigoUnico(prefijo, longitud, validadorUnicidad, MAX_INTENTOS_DEFECTO);
    }

    /**
     * Genera un código único con prefijo personalizado y máximo de intentos configurable
     * 
     * @param prefijo Prefijo del código (ej: "RES", "CLI")
     * @param longitud Longitud del código UUID (caracteres después del prefijo)
     * @param validadorUnicidad Función que valida si el código ya existe (retorna true si existe)
     * @param maxIntentos Máximo de intentos antes de lanzar excepción
     * @return Código único generado
     * @throws RuntimeException Si no se puede generar un código único después de maxIntentos
     */
    public String generarCodigoUnico(String prefijo, int longitud, Predicate<String> validadorUnicidad, int maxIntentos) {
        if (prefijo == null || prefijo.trim().isEmpty()) {
            throw new IllegalArgumentException("El prefijo no puede estar vacío");
        }
        
        if (longitud < 4 || longitud > 32) {
            throw new IllegalArgumentException("La longitud debe estar entre 4 y 32 caracteres");
        }
        
        if (validadorUnicidad == null) {
            throw new IllegalArgumentException("El validador de unicidad no puede ser null");
        }

        String codigo;
        int intentos = 0;
        
        do {
            if (intentos > 0) {
                logger.warn("⚠️ Colisión de código con prefijo '{}'. Reintentando... (Intento {}/{})", 
                    prefijo, intentos, maxIntentos);
            }
            
            // Generar UUID y tomar los primeros N caracteres
            codigo = prefijo + "-" + UUID.randomUUID().toString()
                                          .substring(0, longitud)
                                          .toUpperCase();
            
            intentos++;
            
            // Si llegamos al máximo de intentos, lanzar excepción
            if (intentos > maxIntentos) {
                String mensaje = String.format(
                    "❌ No se pudo generar un código único con prefijo '%s' después de %d intentos. " +
                    "Considera aumentar la longitud del código de %d a %d caracteres.",
                    prefijo, maxIntentos, longitud, longitud + 2
                );
                logger.error(mensaje);
                throw new RuntimeException(mensaje);
            }
            
        } while (validadorUnicidad.test(codigo)); // Continuar mientras el código ya exista
        
        logger.debug("✅ Código único generado: {} (Intentos: {})", codigo, intentos);
        
        return codigo;
    }

    /**
     * Genera un código de reserva único (RES-XXXXXX)
     * 
     * @param validadorUnicidad Función que valida si el código ya existe
     * @return Código de reserva único
     */
    public String generarCodigoReserva(Predicate<String> validadorUnicidad) {
        return generarCodigoUnico("RES", LONGITUD_CODIGO_DEFECTO, validadorUnicidad);
    }

    /**
     * Genera un código de cliente único (CLI-XXXXXX)
     * 
     * @param validadorUnicidad Función que valida si el código ya existe
     * @return Código de cliente único
     */
    public String generarCodigoCliente(Predicate<String> validadorUnicidad) {
        return generarCodigoUnico("CLI", LONGITUD_CODIGO_DEFECTO, validadorUnicidad);
    }

    /**
     * Genera un código genérico con prefijo personalizado
     * Usa longitud por defecto de 6 caracteres
     * 
     * @param prefijo Prefijo del código
     * @param validadorUnicidad Función que valida si el código ya existe
     * @return Código único generado
     */
    public String generarCodigo(String prefijo, Predicate<String> validadorUnicidad) {
        return generarCodigoUnico(prefijo, LONGITUD_CODIGO_DEFECTO, validadorUnicidad);
    }

    /**
     * Genera un token único para ofertas flash, links de recuperación, etc.
     * Usa UUID completo para mayor seguridad (32 caracteres)
     * 
     * @return Token único de 32 caracteres
     */
    public String generarTokenSeguro() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * Genera un token único más corto (16 caracteres)
     * Útil para códigos de verificación, confirmación, etc.
     * 
     * @return Token único de 16 caracteres
     */
    public String generarTokenCorto() {
        return UUID.randomUUID().toString().substring(0, 16).replace("-", "").toUpperCase();
    }
}
