package com.example.tureserva.anotacion;

import com.example.tureserva.modelo.TipoEvento;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target; 

/**
 * Anotación para marcar métodos que deben ser auditados automáticamente.
 * 
 * Uso:
 * <pre>
 * {@code
 * @Auditable(
 *     evento = TipoEvento.CANCHA_CREADA,
 *     descripcion = "Cancha creada",
 *     recursoTipo = "CANCHA"
 * )
 * public void guardarCancha(Cancha cancha) {
 *     // ...
 * }
 * }
 * </pre>
 * 
 * El aspecto AOP interceptará el método y registrará automáticamente:
 * - Tipo de evento
 * - Usuario que realizó la acción
 * - Fecha y hora
 * - IP del cliente
 * - Datos antes/después (si se configuran extractores)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    
    /**
     * Tipo de evento de auditoría.
     * Ejemplo: TipoEvento.CANCHA_CREADA
     */
    TipoEvento evento();
    
    /**
     * Descripción base del evento.
     * Puede usar placeholders que serán reemplazados dinámicamente:
     * - {0}, {1}, {2}... = argumentos del método por posición
     * - {nombre}, {precio}... = propiedades del primer argumento (si es un objeto)
     * 
     * Ejemplos:
     * - "Cancha creada" (estática)
     * - "Cancha '{0}' creada" (usa primer argumento toString())
     * - "Precio de cancha modificado de ${precioAnterior} a ${precioNuevo}"
     */
    String descripcion();
    
    /**
     * Tipo de recurso afectado.
     * Ejemplos: "CANCHA", "SALON", "RESERVA", "POLITICA_CANCELACION"
     * 
     * Por defecto se intenta inferir del nombre del método.
     */
    String recursoTipo() default "";
    
    /**
     * SpEL expression para extraer el ID del complejo.
     * Ejemplos:
     * - "#cancha.complejoDeportivo.id_complejo"
     * - "#reserva.espacio.complejoDeportivo.id_complejo"
     * - "#p0.complejoDeportivo.id_complejo" (primer parámetro)
     * 
     * Si está vacío, el aspecto intentará detectarlo automáticamente.
     */
    String complejoIdExpr() default "";
    
    /**
     * SpEL expression para extraer el nombre del complejo.
     * Similar a complejoIdExpr pero para el nombre.
     * 
     * Si está vacío, el aspecto intentará detectarlo automáticamente.
     */
    String complejoNombreExpr() default "";
    
    /**
     * SpEL expression para extraer el ID del recurso.
     * Ejemplos:
     * - "#cancha.id"
     * - "#reserva.id"
     * - "#result.id" (para métodos que retornan el objeto creado)
     * 
     * Si está vacío, se intentará usar el campo "id" del primer parámetro.
     */
    String recursoIdExpr() default "";
    
    /**
     * Indica si se deben capturar los datos anteriores (para modificaciones).
     * Solo aplica a operaciones UPDATE.
     * 
     * Si es true, el aspecto intentará cargar el objeto desde BD antes de la operación.
     */
    boolean capturarDatosAnteriores() default false;
    
    /**
     * Indica si se deben capturar los datos nuevos (para creaciones/modificaciones).
     * 
     * Si es true, se serializará el objeto resultante a JSON.
     */
    boolean capturarDatosNuevos() default false;
}
